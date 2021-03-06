package mil.dds.anet.test.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

import mil.dds.anet.beans.Organization;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Organization.OrganizationType;
import mil.dds.anet.beans.Person.PersonStatus;
import mil.dds.anet.beans.Person.Role;
import mil.dds.anet.beans.PersonPositionHistory;
import mil.dds.anet.beans.Position;
import mil.dds.anet.beans.Position.PositionStatus;
import mil.dds.anet.beans.Position.PositionType;
import mil.dds.anet.beans.lists.AnetBeanList;
import mil.dds.anet.beans.search.ISearchQuery.SortOrder;
import mil.dds.anet.beans.search.OrganizationSearchQuery;
import mil.dds.anet.beans.search.PositionSearchQuery;
import mil.dds.anet.beans.search.PositionSearchQuery.PositionSearchSortBy;
import mil.dds.anet.test.beans.OrganizationTest;
import mil.dds.anet.test.beans.PositionTest;
import mil.dds.anet.test.resources.utils.GraphQLResponse;

public class PositionResourceTest extends AbstractResourceTest {

	private static final String ORGANIZATION_FIELDS = "uuid shortName";
	private static final String PERSON_FIELDS = "uuid name role";
	private static final String POSITION_FIELDS = "uuid name code type status";
	private static final String FIELDS = POSITION_FIELDS + " person { " + PERSON_FIELDS + " } organization { " + ORGANIZATION_FIELDS + " }";

	@Test
	public void positionTest()
		throws ExecutionException, InterruptedException {
		final Person jack = getJackJackson();
		assertThat(jack.getUuid()).isNotNull();
		assertThat(jack.getPosition()).isNotNull();
		final Position jacksOldPosition = jack.getPosition();
		
		//Create Position
		Position test = new Position();
		test.setName("A Test Position created by PositionResourceTest");
		test.setType(PositionType.ADVISOR);
		test.setStatus(PositionStatus.ACTIVE);
		
		//Assign to an AO
		final String aoUuid = graphQLHelper.createObject(admin, "createOrganization", "organization", "OrganizationInput",
				OrganizationTest.getTestAO(true), new TypeReference<GraphQLResponse<Organization>>() {});
		test.setOrganization(createOrganizationWithUuid(aoUuid));

		String createdUuid = graphQLHelper.createObject(admin, "createPosition", "position", "PositionInput",
				test, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(createdUuid).isNotNull();
		Position created = graphQLHelper.getObjectById(jack, "position", FIELDS, createdUuid, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(created.getName()).isEqualTo(test.getName());
		assertThat(created.getOrganizationUuid()).isEqualTo(aoUuid);
		
		//Assign a person into the position
		Map<String, Object> variables = new HashMap<>();
		variables.put("uuid", created.getUuid());
		variables.put("person", jack);
		Integer nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		Position currPos = graphQLHelper.getObjectById(admin, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPersonUuid()).isNotNull();
		assertThat(currPos.getPersonUuid()).isEqualTo(jack.getUuid());
		
		final Instant jacksTime = Instant.now();
		try {
			Thread.sleep(500);//just slow me down a bit...
		} catch (InterruptedException ignore) {
			/* ignore */
		}  
		
		//change the person in this position
		Person steve = getSteveSteveson();
		final Position stevesCurrentPosition = steve.loadPosition();
		assertThat(stevesCurrentPosition).isNotNull();
		variables = new HashMap<>();
		variables.put("uuid", created.getUuid());
		variables.put("person", steve);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		//Verify that the new person is in the position
		currPos = graphQLHelper.getObjectById(jack, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPerson()).isNotNull();
		assertThat(currPos.getPersonUuid()).isEqualTo(steve.getUuid());
		
		//Verify that the previous person is now no longer in a position
		Person returnedPerson = graphQLHelper.getObjectById(jack, "person", PERSON_FIELDS + " position { " + POSITION_FIELDS + " }", jack.getUuid(), new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(returnedPerson.getPosition()).isNull();
		
		//delete the person from this position
		Integer nrDeleted = graphQLHelper.deleteObject(admin, "deletePersonFromPosition", created.getUuid());
		assertThat(nrDeleted).isEqualTo(1);
		
		currPos = graphQLHelper.getObjectById(jack, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPerson()).isNull();
		
		//Put steve back in his old position
		variables = new HashMap<>();
		variables.put("uuid", stevesCurrentPosition.getUuid());
		variables.put("person", steve);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		currPos = graphQLHelper.getObjectById(jack, "position", FIELDS, stevesCurrentPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPerson()).isNotNull();
		assertThat(currPos.getPersonUuid()).isEqualTo(steve.getUuid());
		
		//pull for the person at a previous time. 
		Position retPos = graphQLHelper.getObjectById(jack, "position", POSITION_FIELDS + " previousPeople { createdAt startTime endTime person { uuid name } }", created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		final List<PersonPositionHistory> previousPeople = retPos.getPreviousPeople();
		assertThat(previousPeople).isNotEmpty();
		PersonPositionHistory last = null;
		for (final PersonPositionHistory personPositionHistory : previousPeople) {
			if (personPositionHistory.getCreatedAt().isBefore(jacksTime)
					&& (last == null || personPositionHistory.getCreatedAt().isAfter(last.getCreatedAt()))) {
				last = personPositionHistory;
			}
		}
		assertThat(last).isNotNull();
		assertThat(last.getPerson()).isNotNull();
		assertThat(last.getPersonUuid()).isEqualTo(jack.getUuid());
		
		created = graphQLHelper.getObjectById(jack, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		List<PersonPositionHistory> history = created.loadPreviousPeople(context).get();
		assertThat(history.size()).isEqualTo(2);
		assertThat(history.get(0).getPositionUuid()).isEqualTo(created.getUuid());
		assertThat(history.get(0).getPersonUuid()).isEqualTo(jack.getUuid());
		assertThat(history.get(0).getStartTime()).isNotNull();
		assertThat(history.get(0).getEndTime()).isNotNull();
		assertThat(history.get(0).getStartTime()).isBefore(history.get(0).getEndTime());
		
		assertThat(history.get(1).loadPerson(context).get()).isEqualTo(steve);
		assertThat(history.get(1).getEndTime()).isNotNull();
		assertThat(history.get(1).getStartTime()).isBefore(history.get(1).getEndTime());
		
		
		//Create a principal
		final OrganizationSearchQuery queryOrgs = new OrganizationSearchQuery();
		queryOrgs.setText("Ministry");
		queryOrgs.setType(OrganizationType.PRINCIPAL_ORG);
		final AnetBeanList<Organization> orgs = graphQLHelper.searchObjects(admin, "organizationList", "query", "OrganizationSearchQueryInput",
				ORGANIZATION_FIELDS, queryOrgs, new TypeReference<GraphQLResponse<AnetBeanList<Organization>>>() {});
		assertThat(orgs.getList().size()).isGreaterThan(0);
			
		Position prinPos = new Position();
		prinPos.setName("A Principal Position created by PositionResourceTest");
		prinPos.setType(PositionType.PRINCIPAL);
		prinPos.setOrganization(orgs.getList().get(0));
		prinPos.setStatus(PositionStatus.ACTIVE);
		
		Person principal = getRogerRogwell();
		assertThat(principal.getUuid()).isNotNull();
		String tashkilUuid = graphQLHelper.createObject(admin, "createPosition", "position", "PositionInput",
				prinPos, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(tashkilUuid).isNotNull();
		Position tashkil = graphQLHelper.getObjectById(admin, "position", FIELDS, tashkilUuid, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(tashkil.getUuid()).isNotNull();
		
		//put the principal in a tashkil
		variables = new HashMap<>();
		variables.put("uuid", tashkil.getUuid());
		variables.put("person", principal);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		//assign the tashkil to the position
		final List<Position> associatedPositions = new ArrayList<>();
		associatedPositions.add(tashkil);
		created.setAssociatedPositions(associatedPositions);
		nrUpdated = graphQLHelper.updateObject(admin, "updateAssociatedPosition", "position", "PositionInput", created);
		assertThat(nrUpdated).isEqualTo(1);
		
		//verify that we can pull the tashkil from the position
		retPos = graphQLHelper.getObjectById(jack, "position", FIELDS + " associatedPositions { " + FIELDS + " }", created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(retPos.getAssociatedPositions().size()).isEqualTo(1);
		assertThat(retPos.getAssociatedPositions()).contains(tashkil);
		
		//delete the tashkil from this position
		retPos.getAssociatedPositions().remove(tashkil);
		nrUpdated = graphQLHelper.updateObject(admin, "updateAssociatedPosition", "position", "PositionInput", retPos);
		assertThat(nrUpdated).isEqualTo(1);
		
		//verify that it's now gone. 
		retPos = graphQLHelper.getObjectById(jack, "position", FIELDS + " associatedPositions { " + FIELDS + " }", created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(retPos.getAssociatedPositions().size()).isEqualTo(0);
		
		//remove the principal from the tashkil
		nrDeleted = graphQLHelper.deleteObject(admin, "deletePersonFromPosition", tashkil.getUuid());
		assertThat(nrDeleted).isEqualTo(1);
		
		//Try to delete this position, it should fail because the tashkil is active
		try {
			graphQLHelper.deleteObject(admin, "deletePosition", tashkil.getUuid());
			fail("Expected BadRequestException");
		} catch (BadRequestException expectedException) {}
		
		tashkil.setStatus(PositionStatus.INACTIVE);
		nrUpdated = graphQLHelper.updateObject(admin, "updatePosition", "position", "PositionInput", tashkil);
		assertThat(nrUpdated).isEqualTo(1);
		
		nrDeleted = graphQLHelper.deleteObject(admin, "deletePosition", tashkil.getUuid());
		assertThat(nrDeleted).isEqualTo(1);

		try {
			graphQLHelper.getObjectById(jack, "position", FIELDS, tashkil.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
			fail("Expected NotFoundException");
		} catch (NotFoundException expectedException) {}

		//Put jack back in his old position
		variables = new HashMap<>();
		variables.put("uuid", jacksOldPosition.getUuid());
		variables.put("person", jack);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);

		currPos = graphQLHelper.getObjectById(admin, "position", FIELDS, jacksOldPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPerson()).isNotNull();
		assertThat(currPos.getPersonUuid()).isEqualTo(jack.getUuid());
	}
		
	
	@Test
	public void tashkilTest() {
		final Person jack = getJackJackson();
		
		//Create Position
		Position test = PositionTest.getTestPosition();
		test.setCode(test.getCode() + "_" + Instant.now().toEpochMilli());
		final OrganizationSearchQuery queryOrgs = new OrganizationSearchQuery();
		queryOrgs.setText("Ministry");
		queryOrgs.setType(OrganizationType.PRINCIPAL_ORG);
		final AnetBeanList<Organization> orgs = graphQLHelper.searchObjects(admin, "organizationList", "query", "OrganizationSearchQueryInput",
				ORGANIZATION_FIELDS, queryOrgs, new TypeReference<GraphQLResponse<AnetBeanList<Organization>>>() {});
		assertThat(orgs.getList().size()).isGreaterThan(0);
		
		test.setOrganization(orgs.getList().get(0));
		
		String createdUuid = graphQLHelper.createObject(admin, "createPosition", "position", "PositionInput",
				test, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(createdUuid).isNotNull();
		Position created = graphQLHelper.getObjectById(admin, "position", FIELDS, createdUuid, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(created.getName()).isEqualTo(test.getName());
		assertThat(created.getCode()).isEqualTo(test.getCode());
		assertThat(created.getUuid()).isNotNull();
		
		//Change Name/Code
		created.setName("Deputy Chief of Donuts");
		Integer nrUpdated = graphQLHelper.updateObject(admin, "updatePosition", "position", "PositionInput", created);
		assertThat(nrUpdated).isEqualTo(1);
		Position returned = graphQLHelper.getObjectById(jack, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(returned.getName()).isEqualTo(created.getName());
		assertThat(returned.getCode()).isEqualTo(created.getCode());
		
		//Assign Principal
		Person steve = getSteveSteveson();
		Position stevesCurrPos = steve.loadPosition();
		assertThat(stevesCurrPos).isNotNull();
		
		Map<String, Object> variables = new HashMap<>();
		variables.put("uuid", created.getUuid());
		variables.put("person", steve);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		Position principalPos = graphQLHelper.getObjectById(admin, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(principalPos.getPerson()).isNotNull();
		assertThat(principalPos.getPersonUuid()).isEqualTo(steve.getUuid());
		
		//Put steve back in his originial position
		variables = new HashMap<>();
		variables.put("uuid", stevesCurrPos.getUuid());
		variables.put("person", steve);
		nrUpdated = graphQLHelper.updateObject(admin, "mutation ($uuid: String!, $person: PersonInput!) { payload: putPersonInPosition (uuid: $uuid, person: $person) }", variables);
		assertThat(nrUpdated).isEqualTo(1);
		
		//Ensure the old position is now empty
		principalPos = graphQLHelper.getObjectById(admin, "position", FIELDS, created.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(principalPos.getPerson()).isNull();
	}
	
	@Test
	public void searchTest() { 
		Person jack = getJackJackson();
		PositionSearchQuery query = new PositionSearchQuery();
		
		//Search by name
		query.setText("Advisor");
		List<Position> searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isNotEmpty();
		
		//Search by name & is not filled
		query.setIsFilled(false);
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isNotEmpty();
		assertThat(searchResults.stream().filter(p -> (p.getPerson() == null)).collect(Collectors.toList()))
			.hasSameElementsAs(searchResults);
		
		//Search by name and is filled and type
		query.setIsFilled(true);
		query.setType(ImmutableList.of(PositionType.ADVISOR));
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isNotEmpty();
		assertThat(searchResults.stream()
				.filter(p -> (p.getPerson() != null))
				.filter(p -> p.getType().equals(PositionType.ADVISOR))
				.collect(Collectors.toList()))
			.hasSameElementsAs(searchResults);
		
		//Search for text= advisor and type = admin should be empty. 
		query.setType(ImmutableList.of(PositionType.ADMINISTRATOR));
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isEmpty();
		
		query.setText("Administrator");
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isNotEmpty();
		
		//Search by organization
		final OrganizationSearchQuery queryOrgs = new OrganizationSearchQuery();
		queryOrgs.setText("ef 1");
		queryOrgs.setType(OrganizationType.ADVISOR_ORG);
		final AnetBeanList<Organization> orgs = graphQLHelper.searchObjects(jack, "organizationList", "query", "OrganizationSearchQueryInput",
				ORGANIZATION_FIELDS, queryOrgs, new TypeReference<GraphQLResponse<AnetBeanList<Organization>>>() {});
		assertThat(orgs.getList().size()).isGreaterThan(0);
		Organization ef11 = orgs.getList().stream().filter(o -> o.getShortName().equalsIgnoreCase("ef 1.1")).findFirst().get();
		Organization ef1 = orgs.getList().stream().filter(o -> o.getShortName().equalsIgnoreCase("ef 1")).findFirst().get();
		assertThat(ef11.getShortName()).isEqualToIgnoringCase("EF 1.1");
		assertThat(ef1.getShortName()).isEqualTo("EF 1");
		
		query.setText("Advisor");
		query.setType(null);
		query.setOrganizationUuid(ef1.getUuid());
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults.stream()
				.filter(p -> p.getOrganizationUuid() == ef1.getUuid())
				.collect(Collectors.toList()))
			.hasSameElementsAs(searchResults);
		
		query.setIncludeChildrenOrgs(true);
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults).isNotEmpty();
		
		query.setIncludeChildrenOrgs(false);
		query.setText("a");
		query.setSortBy(PositionSearchSortBy.NAME);
		query.setSortOrder(SortOrder.DESC); 
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		String prevName = null;
		for (Position p : searchResults) { 
			if (prevName != null) { assertThat(p.getName().compareToIgnoreCase(prevName)).isLessThanOrEqualTo(0); } 
			prevName = p.getName();
		}
		
		query.setSortBy(PositionSearchSortBy.CODE);
		query.setSortOrder(SortOrder.ASC); 
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		String prevCode = null;
		for (Position p : searchResults) { 
			if (prevCode != null) { assertThat(p.getCode().compareToIgnoreCase(prevCode)).isGreaterThanOrEqualTo(0); } 
			prevCode = p.getCode();
		}
		
		//search by status. 
		query = new PositionSearchQuery();
		query.setStatus(PositionStatus.INACTIVE);
		searchResults = graphQLHelper.searchObjects(jack, "positionList", "query", "PositionSearchQueryInput",
				FIELDS, query, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {}).getList();
		assertThat(searchResults.size()).isGreaterThan(0);
		assertThat(searchResults.stream().filter(p -> p.getStatus().equals(PositionStatus.INACTIVE)).count()).isEqualTo(searchResults.size());
	}
	
	@Test
	public void getAllPositionsTest() { 
		Person jack = getJackJackson();
		
		int pageNum = 0;
		int pageSize = 10;
		int totalReturned = 0;
		int firstTotalCount = 0;
		AnetBeanList<Position> list = null;
		do { 
			list = graphQLHelper.getAllObjects(jack, "positions (pageNum: " + pageNum + ", pageSize: " + pageSize + ")",
					FIELDS, new TypeReference<GraphQLResponse<AnetBeanList<Position>>>() {});
			assertThat(list).isNotNull();
			assertThat(list.getPageNum()).isEqualTo(pageNum);
			assertThat(list.getPageSize()).isEqualTo(pageSize);
			totalReturned += list.getList().size();
			if (pageNum == 0) { firstTotalCount = list.getTotalCount(); }
			pageNum++;
		} while (list.getList().size() != 0); 
		
		assertThat(totalReturned).isEqualTo(firstTotalCount);
	}
	
	@Test
	public void createPositionTest()
		throws ExecutionException, InterruptedException {
		//Create a new position and designate the person upfront
		Person newb = new Person();
		newb.setName("PositionTest Person");
		newb.setRole(Role.PRINCIPAL);
		newb.setStatus(PersonStatus.ACTIVE);
		
		String newbUuid = graphQLHelper.createObject(admin, "createPerson", "person", "PersonInput",
				newb, new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(newbUuid).isNotNull();
		newb = graphQLHelper.getObjectById(admin, "person", PERSON_FIELDS, newbUuid, new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(newb.getUuid()).isNotNull();
		
		final OrganizationSearchQuery queryOrgs = new OrganizationSearchQuery();
		queryOrgs.setText("Ministry");
		queryOrgs.setType(OrganizationType.PRINCIPAL_ORG);
		final AnetBeanList<Organization> orgs = graphQLHelper.searchObjects(admin, "organizationList", "query", "OrganizationSearchQueryInput",
				ORGANIZATION_FIELDS, queryOrgs, new TypeReference<GraphQLResponse<AnetBeanList<Organization>>>() {});
		assertThat(orgs.getList().size()).isGreaterThan(0);
		
		Position newbPosition = new Position();
		newbPosition.setName("PositionTest Position for Newb");
		newbPosition.setType(PositionType.PRINCIPAL);
		newbPosition.setOrganization(orgs.getList().get(0));
		newbPosition.setStatus(PositionStatus.ACTIVE);
		newbPosition.setPerson(newb);
		
		String newbPositionUuid = graphQLHelper.createObject(admin, "createPosition", "position", "PositionInput",
				newbPosition, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(newbPositionUuid).isNotNull();
		newbPosition = graphQLHelper.getObjectById(admin, "position", FIELDS, newbPositionUuid, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(newbPosition.getUuid()).isNotNull();
		
		//Ensure that the position contains the person
		Position returned = graphQLHelper.getObjectById(admin, "position", FIELDS, newbPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(returned.getUuid()).isNotNull();
		final Person returnedPerson = returned.getPerson();
		assertThat(returnedPerson).isNotNull();
		assertThat(returnedPerson.getUuid()).isEqualTo(newb.getUuid());
		
		//Ensure that the person is assigned to this position. 
		assertThat(newb.loadPosition()).isNotNull();
		assertThat(newb.getPosition().getUuid()).isEqualTo(returned.getUuid());
		
		//Assign somebody else to this position. 
		Person prin2 = new Person();
		prin2.setName("2nd Principal in PrincipalTest");
		prin2.setRole(Role.PRINCIPAL);
		String prin2Uuid = graphQLHelper.createObject(admin, "createPerson", "person", "PersonInput",
				prin2, new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(prin2Uuid).isNotNull();
		prin2 = graphQLHelper.getObjectById(admin, "person", PERSON_FIELDS, prin2Uuid, new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(prin2.getUuid()).isNotNull();
		assertThat(prin2.loadPosition()).isNull();

		final Position prin2Position = new Position();
		prin2Position.setUuid(newbPosition.getUuid());
		prin2.setPosition(prin2Position);
		Integer nrUpdated = graphQLHelper.updateObject(admin, "updatePerson", "person", "PersonInput", prin2);
		assertThat(nrUpdated).isEqualTo(1);
		
		//Reload this person to check their position was set. 
		prin2 = graphQLHelper.getObjectById(admin, "person", PERSON_FIELDS, prin2.getUuid(), new TypeReference<GraphQLResponse<Person>>() {});
		assertThat(prin2).isNotNull();
		assertThat(prin2.loadPosition()).isNotNull();
		assertThat(prin2.getPosition().getUuid()).isEqualTo(newbPosition.getUuid());
		
		//Check with a different API endpoint. 
		Position currPos = graphQLHelper.getObjectById(admin, "position", FIELDS, newbPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPersonUuid()).isNotNull();
		assertThat(currPos.getPersonUuid()).isEqualTo(prin2.getUuid());
		
		//Slow the test down a bit
		try {
			Thread.sleep(10);
		} catch (InterruptedException ignore) { }
		
		//Create a new position and move prin2 there on CREATE. 
		Position pos2 = new Position();
		pos2.setName("Created by PositionTest");
		pos2.setType(PositionType.PRINCIPAL);
		pos2.setOrganization(orgs.getList().get(0));
		pos2.setStatus(PositionStatus.ACTIVE);
		pos2.setPerson(Person.createWithUuid(prin2.getUuid()));
		
		String pos2Uuid = graphQLHelper.createObject(admin, "createPosition", "position", "PositionInput",
				pos2, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(pos2Uuid).isNotNull();
		pos2 = graphQLHelper.getObjectById(admin, "position", FIELDS, pos2Uuid, new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(pos2.getUuid()).isNotNull();
		
		returned = graphQLHelper.getObjectById(admin, "position", FIELDS, pos2.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(returned).isNotNull();
		assertThat(returned.getName()).isEqualTo(pos2.getName());
		final Person returnedPerson2 = returned.getPerson();
		assertThat(returnedPerson2).isNotNull();
		assertThat(returnedPerson2.getUuid()).isEqualTo(prin2.getUuid());
		
		//Make sure prin2 got moved out of newbPosition
		currPos = graphQLHelper.getObjectById(admin, "position", FIELDS, newbPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		assertThat(currPos.getPerson()).isNull();
		
		//Pull the history of newbPosition
		newbPosition = graphQLHelper.getObjectById(admin, "position", FIELDS, newbPosition.getUuid(), new TypeReference<GraphQLResponse<Position>>() {});
		List<PersonPositionHistory> history = newbPosition.loadPreviousPeople(context).get();
		assertThat(history.size()).isEqualTo(2);
		assertThat(history.get(0).getPersonUuid()).isEqualTo(newb.getUuid());
		assertThat(history.get(1).getPersonUuid()).isEqualTo(prin2.getUuid());
	}

}
