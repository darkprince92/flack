package flack.outputdirected;

import edu.mit.csail.sdg.alloy4.Err;
import flack.locator.Locator;

public class LocateFaultDemo {
	public static void main(String[] args) throws Err {
//		String path = "outputdirectedtests/singly-list.als";
		String path = "benchmark/alloyfl/arr1.als";
		Locator locator = new Locator(false);
		locator.localize(path, 5);
	}
}
