package mil.dds.anet.database;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.GeneratedKeys;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import mil.dds.anet.beans.Billet;
import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Tashkil;
import mil.dds.anet.database.mappers.BilletMapper;
import mil.dds.anet.database.mappers.PersonMapper;
import mil.dds.anet.database.mappers.TashkilMapper;

@RegisterMapper(TashkilMapper.class)
public class TashkilDao implements IAnetDao<Tashkil>  {

	Handle dbHandle;
	
	public TashkilDao(Handle h) { 
		this.dbHandle = h;
	}
	
	@Override
	public List<Tashkil> getAll(int pageNum, int pageSize) {
		Query<Tashkil> query = dbHandle.createQuery("SELECT * from tashkils ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
				.bind("limit", pageSize)
				.bind("offset", pageSize * pageNum)
				.map(new TashkilMapper());
			return query.list();
	}
	
	public Tashkil getById(@Bind("id") int id) { 
		List<Tashkil> query = dbHandle.createQuery("SELECT * from tashkils where id = :id")
			.bind("id",id)
			.map(new TashkilMapper())
			.list();
		if (query.size() == 0) { return null; } 
		return query.get(0);			
	}
	
	@Override
	public Tashkil insert(Tashkil t) {
		t.setCreatedAt(DateTime.now());
		t.setUpdatedAt(DateTime.now());
		GeneratedKeys<Map<String, Object>> keys = dbHandle.createStatement(
				"INSERT INTO tashkils (code, name, createdAt, updatedAt) " +  
				"VALUES (:code, :name, :createdAt, :updatedAt)")
			.bindFromProperties(t)
			.executeAndReturnGeneratedKeys();
		t.setId((Integer)keys.first().get("last_insert_rowid()"));
		return t;
	}
	
	@Override
	public int update(Tashkil t) {
		return dbHandle.createStatement("UPDATE tashkils SET name = :name, code = :code, updatedAt = :updatedAt WHERE id = :id")
				.bindFromProperties(t)
				.execute();
	}
	
	public List<Tashkil> getByCode(String code) { 
		return dbHandle.createQuery("SELECT * from tashkils where code = :code")
			.bind("code", code)
			.map(new TashkilMapper())
			.list();
	}
	
	public List<Tashkil> getByCodePrefix(@Bind("code") String code) { 
		return dbHandle.createQuery("SELECT * from tashkils where code LIKE :code || '%'")
				.bind("code", code)
				.map(new TashkilMapper())
				.list();
		}
	
	public Person getPrincipalInTashkilNow(Tashkil t) { 
		return getPrincipalInTashkil(t, DateTime.now());
	}
	
	public Person getPrincipalInTashkil(Tashkil t, DateTime dtg) { 
		List<Person> results = dbHandle.createQuery("SELECT people.* FROM tashkilPrincipals " + 
					"LEFT JOIN people ON people.id = tashkilPrincipals.principalId " + 
					"WHERE tashkilPrincipals.tashkilId = :tashkilId " +
					"AND tashkilPrincipals.createdAt < :dtg " + 
					"ORDER BY tashkilPrincipals.createdAt DESC LIMIT 1")
				.bind("tashkilId", t.getId())
				.bind("dtg", dtg)
				.map(new PersonMapper())
				.list();
		if (results.size() == 0) { return null; } 
		return results.get(0);
	}
	
	public Tashkil getTashkilForPrincipal(Person p) {
		List<Tashkil> tashkils = dbHandle.createQuery("SELECT tashkils.* from tashkilPrincipals " +
				"LEFT JOIN tashkils ON tashkilPrincipals.tashkilId = tashkils.id " +
				"WHERE tashkilPrincipals.principalId = :principalsId " +
				"ORDER BY tashkilPrincipals.createdAt DESC LIMIT 1")
			.bind("principalsId", p.getId())
			.map(new TashkilMapper())
			.list();
		if (tashkils.size() == 0) { return null; } 
		return tashkils.get(0);		
	}
	
	public int setPrincipal(int tashkilId, int principalId, DateTime dtg) { 
		return dbHandle.createStatement("INSERT INTO tashkilPrincipals (tashkilId, principalId, createdAt) VALUES (:tashkilId, :principalId, :dtg)")
				.bind("tashkilId", tashkilId)
				.bind("principalId", principalId)
				.bind("dtg", dtg)
				.execute();
	}
	
	public List<Tashkil> getEmptyTashkils() {
		return dbHandle.createQuery("SELECT tashkils.* FROM tashkils INNER JOIN " + 
				"(SELECT tashkilId, principalId, MAX(createdAt) FROM tashkilPrincipals GROUP BY tashkilId) emptyTashkils " +
				"ON tashkils.id = emptyTashkils.tashkilId WHERE emptyTashkils.principalId is null")
			.map(new TashkilMapper())
			.list();
	}

}
