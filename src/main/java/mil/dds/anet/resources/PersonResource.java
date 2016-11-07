package mil.dds.anet.resources;

import java.util.List;

import javax.annotation.security.PermitAll;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import mil.dds.anet.AnetObjectEngine;
import mil.dds.anet.beans.Billet;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Person.Role;
import mil.dds.anet.beans.Tashkil;
import mil.dds.anet.database.PersonDao;
import mil.dds.anet.views.ObjectListView;

@Path("/people")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PersonResource {

	private PersonDao dao;
	
	public PersonResource(AnetObjectEngine engine) { 
		this.dao = engine.getPersonDao();
	}
	
	/**
	 * Returns all people objects in the ANET system. Does no filtering on role/status/etc. 
	 * @param pageNum 0 indexed page number of results to get. Defaults to 0. 
	 * @param pageSize Defaults to 100
	 * @return List of People objects in the system
	 */
	@GET
	@Path("/")
	public List<Person> getAllPeople(@DefaultValue("0") @QueryParam("pageNum") int pageNum, @DefaultValue("100") @QueryParam("pageSize") int pageSize) {
		return dao.getAll(pageNum, pageSize);
	}
	
	/**
	 * same as {@link #getAllPeople(int, int)} but generates the HTML view (/person/view/index.ftl)
	 */
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	public ObjectListView<Person> getAllPeopleView(@DefaultValue("0") @QueryParam("pageNum") int pageNum, @DefaultValue("100") @QueryParam("pageSize") int pageSize) {
		return new ObjectListView<Person>(dao.getAll(pageNum, pageSize), Person.class);
	}
	
	
	/**
	 * Returns a single person entry based on ID. 
	 */
	@GET
	@Path("/{id}")
	@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
	public Person getViewById(@PathParam("id") int id) { 
		Person p = dao.getById(id);
		p.addToContext("billet", dao.getBilletForAdvisor(p.getId()));
		return p.render("show.ftl");
	}
	
	
	@GET
	@Path("/new")
	@Produces(MediaType.TEXT_HTML)
	public Person getPersonForm() { 
		return (new Person()).render("form.ftl");
	}
	
	/**
	 * Creates a new {@link Person} object as supplied in http entity. 
	 * @return the same Person object with the ID field filled in. 
	 */
	@POST
	@Path("/new")
	public Person createNewPerson(Person p) { 
		return dao.insert(p);
	}
	
	
	@GET
	@Path("/{id}/edit")
	@Produces(MediaType.TEXT_HTML)
	public Person getPersonEditForm(@PathParam("id") int id) { 
		Person p = dao.getById(id);
		return p.render("form.ftl");
	}
	
	/**
	 * Will update a person record with the {@link Person} entity provided in the http entity. All fields will be updated, so you must pass the complete Person object. 
	 * @return HTTP/200 on success, HTTP/404 on any error. 
	 */
	@POST
	@Path("/update")
	public Response updatePerson(Person p) { 
		int numRows = dao.update(p);
		return (numRows == 1) ? Response.ok().build() : Response.status(Status.NOT_FOUND).build();
	}
	
//	@DELETE
//	@Path("/{id}")
//	public Response deletePerson(@PathParam("id") int id) {
//		//TODO: should this operation be allowed?
		//TODO: no, this should soft delete! 
//		dao.deletePersonById(id);
//		return Response.ok().build();
//	}
	
	/**
	 * Searches people in the ANET database TODO: should be fuzzy searching
	 * @param query the search term
	 * @param role either PRINCIPAL, or ADVISOR will search people with that role. 
	 * @return a list of people objects
	 */
	@GET
	@Path("/search")
	public List<Person> searchByName(@QueryParam("q") String query, @QueryParam("role") Role role) {
		return dao.searchByName(query, role);
	}
	
	@GET
	@Path("/search")
	@Produces(MediaType.TEXT_HTML)
	public Person getSearchPage() { 
		return (new Person()).render("search.ftl");
	}
	
	/**
	 * Fetches the current billet that a given person (Advisor) is in. 
	 * @param personId the ID number of the person whose billet you want to lookup
	 */
	@GET
	@Path("/{id}/billet")
	public Billet getBilletForAdvisor(@PathParam("personId") int personId) { 
		return dao.getBilletForAdvisor(personId);
	}
	
	/**
	 * Fetches the current tashkil that a given person (Principal) is in. 
	 * @param personId the ID number of the person whose tashkil you want to lookup
	 */
	@GET
	@Path("/{id}/tashkil")
	public Tashkil getTashkilForPrincipal(@PathParam("personId") int personId) {
		return null;
//		return dao.getTashkilForPrincipal(personId);
	}
}
