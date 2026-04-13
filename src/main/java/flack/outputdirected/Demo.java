package flack.outputdirected;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.parser.CompModule;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;
import flack.compare.InstancePair;

public class Demo {
	public static void main(String[] args) throws Err {

		A4Options options = new A4Options();
		options.solver = A4Options.SatSolver.SAT4J;

		String alloyCode = DummyModel.getAlloyCode();
		System.out.println(alloyCode);
		CompModule world = CompUtil.parseEverything_fromString(null, alloyCode.toString());
		Command faultyWithAttrCmd = world.getAllCommands().get(0);
		Command notFaultyCmd = world.getAllCommands().get(1);
		A4Solution faultyInstance = TranslateAlloyToKodkod.execute_command(null, world.getAllSigs(), faultyWithAttrCmd, options);
		System.out.println("Faulty Instance: ");
		System.out.println(faultyInstance);
		A4Solution editedInstance = TranslateAlloyToKodkod.execute_command_with_target(null, world.getAllSigs(), notFaultyCmd, options, faultyInstance);
		System.out.println("Edited Instance: ");
		System.out.println(editedInstance);

		FaultFromInstances faultFromInstances = new FaultFromInstances(alloyCode, null, world);
		InstancePair ip = faultFromInstances.genInstancePair(faultyInstance, notFaultyCmd);
		faultFromInstances.findFaultsFromInstancePair(ip, notFaultyCmd);
	}
}
