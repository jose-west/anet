package mil.dds.anet.database.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.mapper.RowMapper;

import mil.dds.anet.beans.ApprovalStep;
import mil.dds.anet.utils.DaoUtils;

public class ApprovalStepMapper implements RowMapper<ApprovalStep> {

	@Override
	public ApprovalStep map(ResultSet r, StatementContext ctx) throws SQLException {
		ApprovalStep step = new ApprovalStep();
		DaoUtils.setCommonBeanFields(step, r, null);
		step.setNextStepUuid(r.getString("nextStepUuid"));
		step.setAdvisorOrganizationUuid(r.getString("advisorOrganizationUuid"));
		step.setName(r.getString("name"));
		
		return step;
	}

}
