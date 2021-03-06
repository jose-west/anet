package mil.dds.anet.test.beans;

import org.junit.Test;

import mil.dds.anet.beans.Task;

public class TaskTest extends BeanTester<Task> {

	public static Task getTestTask() { 
		Task p = new Task();
		p.setShortName("F-1");
		p.setLongName("Run the bases");
		p.setCategory("Functional Area");
		return p;
	}
	
	@Test
	public void serializesToJson() throws Exception {
		serializesToJson(getTestTask(), "testJson/task/testTask.json");
	}
	
	@Test
	public void deserializesFromJson() throws Exception {
		deserializesFromJson(getTestTask(), "testJson/task/testTask.json");
    }
}
