package mil.dds.anet.search;

import org.jdbi.v3.core.Handle;

import mil.dds.anet.beans.Person;
import mil.dds.anet.beans.Report;
import mil.dds.anet.beans.lists.AnetBeanList;
import mil.dds.anet.beans.search.ReportSearchQuery;

public interface IReportSearcher {

	public AnetBeanList<Report> runSearch(ReportSearchQuery query, Handle dbHandle, Person user);
	
}
