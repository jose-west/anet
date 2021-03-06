package mil.dds.anet.database;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.MapMapper;

import mil.dds.anet.beans.ApprovalStep;
import mil.dds.anet.beans.Position;
import mil.dds.anet.beans.lists.AnetBeanList;
import mil.dds.anet.database.mappers.ApprovalStepMapper;
import mil.dds.anet.database.mappers.PositionMapper;
import mil.dds.anet.utils.DaoUtils;
import mil.dds.anet.views.ForeignKeyFetcher;

public class ApprovalStepDao implements IAnetDao<ApprovalStep> {

	private final Handle dbHandle;
	private final IdBatcher<ApprovalStep> idBatcher;
	private final ForeignKeyBatcher<Position> approversBatcher;
	private final ForeignKeyBatcher<ApprovalStep> organizationIdBatcher;

	public ApprovalStepDao(Handle h) {
		this.dbHandle = h;
		final String idBatcherSql = "/* batch.getApprovalStepsByUuids */ SELECT * from \"approvalSteps\" where uuid IN ( <uuids> )";
		this.idBatcher = new IdBatcher<ApprovalStep>(dbHandle, idBatcherSql, "uuids", new ApprovalStepMapper());

		final String approversBatcherSql = "/* batch.getApproversForStep */ SELECT \"approvalStepUuid\", " + PositionDao.POSITIONS_FIELDS
				+ " FROM approvers "
				+ "LEFT JOIN positions ON \"positions\".\"uuid\" = approvers.\"positionUuid\" "
				+ "WHERE \"approvalStepUuid\" IN ( <foreignKeys> )";
		this.approversBatcher = new ForeignKeyBatcher<Position>(h, approversBatcherSql, "foreignKeys", new PositionMapper(), "approvalStepUuid");

		final String organizationIdBatcherSql = "/* batch.getApprovalStepsByOrg */ SELECT * from \"approvalSteps\" WHERE \"advisorOrganizationUuid\" IN ( <foreignKeys> )";
		this.organizationIdBatcher = new ForeignKeyBatcher<ApprovalStep>(h, organizationIdBatcherSql, "foreignKeys", new ApprovalStepMapper(), "advisorOrganizationUuid");
	}
	
	public AnetBeanList<?> getAll(int pageNum, int pageSize) {
		throw new UnsupportedOperationException();
	}

	public CompletableFuture<List<ApprovalStep>> getByAdvisorOrganizationUuid(Map<String, Object> context, String aoUuid) {
		return new ForeignKeyFetcher<ApprovalStep>()
				.load(context, "organization.approvalSteps", aoUuid);
	}

	@Override
	public ApprovalStep getByUuid(String uuid) {
		return getByIds(Arrays.asList(uuid)).get(0);
	}

	@Override
	public List<ApprovalStep> getByIds(List<String> uuids) {
		return idBatcher.getByIds(uuids);
	}

	public List<List<Position>> getApprovers(List<String> foreignKeys) {
		return approversBatcher.getByForeignKeys(foreignKeys);
	}

	public List<List<ApprovalStep>> getApprovalSteps(List<String> foreignKeys) {
		return organizationIdBatcher.getByForeignKeys(foreignKeys);
	}

	@Override
	public ApprovalStep insert(ApprovalStep as) {
		DaoUtils.setInsertFields(as);
		dbHandle.createUpdate(
				"/* insertApprovalStep */ INSERT into \"approvalSteps\" (uuid, name, \"nextStepUuid\", \"advisorOrganizationUuid\") "
				+ "VALUES (:uuid, :name, :nextStepUuid, :advisorOrganizationUuid)")
			.bindBean(as)
			.execute();

		if (as.getApprovers() != null) {
			for (Position approver : as.getApprovers()) {
				if (approver.getUuid() == null) {
					throw new WebApplicationException("Invalid Position UUID of Null for Approver");
				}
				dbHandle.createUpdate("/* insertApprovalStep.approvers */ "
						+ "INSERT INTO approvers (\"positionUuid\", \"approvalStepUuid\") VALUES (:positionUuid, :stepUuid)")
					.bind("positionUuid", approver.getUuid())
					.bind("stepUuid", as.getUuid())
					.execute();
			}
		}
		
		return as;
	}
	
	/**
	 * Inserts this approval step at the end of the organizations Approval Chain.
	 */
	public ApprovalStep insertAtEnd(ApprovalStep as) {
		as = insert(as);
		
		//Add this Step to the current org list. 
		dbHandle.createUpdate("/* insertApprovalAtEnd */ UPDATE \"approvalSteps\" SET \"nextStepUuid\" = :uuid "
				+ "WHERE \"advisorOrganizationUuid\" = :advisorOrganizationUuid "
				+ "AND \"nextStepUuid\" IS NULL AND uuid != :uuid")
			.bindBean(as)
			.execute();
		return as;
	}
	
	/**
	 * Updates the name, nextStepUuid, and advisorOrgUuid on this Approval Step
	 * DOES NOT update the list of members for this step. 
	 */
	public int update(ApprovalStep as) {
		DaoUtils.setUpdateFields(as);
		return dbHandle.createUpdate("/* updateApprovalStep */ UPDATE \"approvalSteps\" SET name = :name, "
				+ "\"nextStepUuid\" = :nextStepUuid, \"advisorOrganizationUuid\" = :advisorOrganizationUuid "
				+ "WHERE uuid = :uuid")
			.bindBean(as)
			.execute();
	}

	/**
	 * Delete the Approval Step with the given UUID.
	 * Will patch up the Approval Process list after the removal. 
	 */
	public boolean deleteStep(String uuid) {
		//ensure there is nothing currently on this step
		List<Map<String, Object>> rs = dbHandle.select("/* deleteApproval.check */ SELECT count(*) AS ct FROM reports WHERE \"approvalStepUuid\" = ?", uuid)
				.map(new MapMapper(false))
				.list();
		Map<String,Object> result = rs.get(0);
		int count = ((Number) result.get("ct")).intValue();
		if (count != 0) {
			throw new WebApplicationException("Reports are currently pending at this step", Status.NOT_ACCEPTABLE);
		}

		//fix up the linked list.
		dbHandle.createUpdate("/* deleteApproval.update */ UPDATE \"approvalSteps\" "
				+ "SET \"nextStepUuid\" = (SELECT \"nextStepUuid\" from \"approvalSteps\" where uuid = :stepToDeleteUuid) "
				+ "WHERE \"nextStepUuid\" = :stepToDeleteUuid")
			.bind("stepToDeleteUuid", uuid)
			.execute();

		//Remove all approvers from this step
		dbHandle.execute("/* deleteApproval.delete1 */ DELETE FROM approvers where \"approvalStepUuid\" = ?", uuid);

		//Update any approvals that happened at this step
		dbHandle.execute("/* deleteApproval.updateActions */ UPDATE \"approvalActions\" SET \"approvalStepUuid\" = ? WHERE \"approvalStepUuid\" = ?", null, uuid);

		dbHandle.execute("/* deleteApproval.delete2 */ DELETE FROM \"approvalSteps\" where uuid = ?", uuid);
		return true;
	}

	/**
	 * Returns the previous step for a given stepUuid.
	 */
	public ApprovalStep getStepByNextStepUuid(String uuid) {
		List<ApprovalStep> list = dbHandle.createQuery("/* getNextStep */ SELECT * FROM \"approvalSteps\" WHERE \"nextStepUuid\" = :uuid")
			.bind("uuid", uuid)
			.map(new ApprovalStepMapper())
			.list();
		if (list.size() == 0) { return null; }
		return list.get(0);
	}
	
	/**
	 * Returns the list of positions that can approve for a given step. 
	 */
	public CompletableFuture<List<Position>> getApproversForStep(Map<String, Object> context, String approvalStepUuid) {
		return new ForeignKeyFetcher<Position>()
				.load(context, "approvalStep.approvers", approvalStepUuid);
	}

	public int addApprover(ApprovalStep step, String positionUuid) {
		return dbHandle.createUpdate("/* addApprover */ INSERT INTO approvers (\"approvalStepUuid\", \"positionUuid\") VALUES (:stepUuid, :positionUuid)")
				.bind("stepUuid", step.getUuid())
				.bind("positionUuid", positionUuid)
				.execute();
	}
	
	public int removeApprover(ApprovalStep step, String positionUuid) {
		return dbHandle.createUpdate("/* removeApprover */ DELETE FROM approvers WHERE \"approvalStepUuid\" = :stepUuid AND \"positionUuid\" = :positionUuid")
				.bind("stepUuid", step.getUuid())
				.bind("positionUuid", positionUuid)
				.execute();
	}
}
