package mil.dds.anet.database;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.GeneratedKeys;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import mil.dds.anet.beans.Poam;
import mil.dds.anet.database.mappers.PoamMapper;

@RegisterMapper(PoamMapper.class)
public class PoamDao implements IAnetDao<Poam> {

	Handle dbHandle;
	
	public PoamDao(Handle h) { 
		this.dbHandle = h; 
	}
	
	public List<Poam> getAll(int pageNum, int pageSize) { 
		Query<Poam> query = dbHandle.createQuery("SELECT * from poams ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
				.bind("limit", pageSize)
				.bind("offset", pageSize * pageNum)
				.map(new PoamMapper());
			return query.list();
	}
	
	@Override
	public Poam getById(int id) { 
		Query<Poam> query = dbHandle.createQuery("SELECT * from poams where id = :id")
			.bind("id",id)
			.map(new PoamMapper());
		List<Poam> results = query.list();
		if (results.size() == 0) { return null; } 
		return results.get(0);
	}
	
	@Override
	public Poam insert(Poam p){
		p.setCreatedAt(DateTime.now());
		p.setUpdatedAt(DateTime.now());
		GeneratedKeys<Map<String, Object>> keys = dbHandle.createStatement("INSERT INTO poams (longName, shortName, category, parentPoamId) " + 
				"VALUES (:longName, :shortName, :category, :parentPoamId)")
			.bindFromProperties(p)
			.executeAndReturnGeneratedKeys();
		p.setId((Integer)keys.first().get("last_insert_rowid()"));
		return p;
	}
	
	@Override
	public int update(Poam p) { 
		p.setUpdatedAt(DateTime.now());
		return dbHandle.createStatement("UPDATE poams set longName = :longName, shortName = :shortName " + 
				"category = :category, parentPoamId = :parentPoamId " + 
				"WHERE id = :id")
			.bindFromProperties(p)
			.execute();
	}
	
	public List<Poam> getPoamsByParentId(int parentPoamId) { 
		Query<Poam> query = dbHandle.createQuery("SELECT * from poams where parentPoamId = :parentPoamId")
			.bind("parentPoamId", parentPoamId)
			.map(new PoamMapper());
		return query.list();
	}

	public List<Poam> getPoamsByCategory(String category) { 
		Query<Poam> query = dbHandle.createQuery("SELECT * from poams WHERE category = :cat")
			.bind("cat", category)
			.map(new PoamMapper());
		return query.list();
	}
}
