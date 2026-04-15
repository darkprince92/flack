package flack.outputdirected;

import flack.compare.BugType;
import flack.compare.InstancePair;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.parser.CompModule;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;
import flack.parser.ast.AModel;
import flack.parser.ast.Exprn;
import flack.utility.AlloyUtil;
import flack.utility.NodeCounter;

import java.util.HashSet;
import java.util.Set;

public class FaultFromInstances {

	public String model;
	public A4Reporter rep;
	public CompModule world;

	public FaultFromInstances(String model, A4Reporter rep, CompModule world) {
		this.model = model;
		this.rep = rep;
		this.world = world;
	}

//	public void locateFaultsFromInstances(A4Solution faultyInstance, Command pred){
//
//	}

	public InstancePair genInstancePair(A4Solution faultyInstance, Command pred) throws Err {
		A4Options opt = new A4Options();
		/* set solver to MiniSat with prover to extract unsat core when needed */
		opt.solver = A4Options.SatSolver.MiniSatProverJNI;
		try {
//			A4Solution counterExample = TranslateAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), asst, opt);
			A4Solution counterExample = faultyInstance;
			if(!counterExample.satisfiable())
				return new InstancePair(BugType.NO_BUG);
			A4Solution expectedInst = TranslateAlloyToKodkod.execute_command_with_target(rep, world.getAllSigs(), pred, opt, counterExample);
			/* if expected instance is unsat, remove unsat core and generate new expected instance */
			if(!expectedInst.satisfiable()){
				A4Solution unsatSol = TranslateAlloyToKodkod.execute_command(rep, world.getAllSigs(), pred, opt);
				/*TODO: remove expr in repair_pred, though it doesn't matter, as it will always eval to true*/
				AModel sliceModel = new AModel(world, unsatSol.unsatObj());
				CompModule slicedWorld = CompUtil.parseEverything_fromString(new A4Reporter(), sliceModel.toString());
				for( Command cmd : slicedWorld.getAllCommands() ){
					if( cmd.label.equals(pred.label) ){
						expectedInst = TranslateAlloyToKodkod.execute_command_with_target(rep, slicedWorld.getAllSigs(), cmd, opt,
								counterExample);
					}
				}
				Set<Exprn> locations = new HashSet<>();
				for(Expr expr : unsatSol.unsatExpr() )
				{
					Exprn exprn = Exprn.parseExpr(null, expr);
					if((boolean)expectedInst.eval( CompUtil.parseOneExpression_fromString(slicedWorld, exprn.toString())) == false ){
						locations.add(exprn);
					}
				}
				return new InstancePair(slicedWorld, counterExample, expectedInst, locations, BugType.NO_EXPECT);
			}

		} catch(Err err){
			err.printStackTrace();
			throw err;
		}
		return null;
	}

	public void findFaultsFromInstancePair(InstancePair ip, Command pred){
//		if(ip.bugType == BugType.NO_BUG) {
//			if(i == 1){
//				System.out.println("No assertion violated");
//				continue outloop;
//			}else{
//				ips.remove(ip);
//			}
//		}
		AModel aModel = new AModel(world);
		if (ip.bugType == BugType.NO_EXPECT) {
//			long analyseTime = System.currentTimeMillis() - beginTime;
			// System.out.println("type b bug");
			System.out.println("RANK LIST:");
			NodeCounter nc = new NodeCounter();
			for (Exprn exprn : ip.locations) {
				AlloyUtil.count(nc, exprn);
				System.out.println(exprn.toString());
			}
			//System.out.println("# val: " + valnum / ips.size());
			// System.out.println("# Slice Out: " + (counter.count - collected));
			NodeCounter counter = new NodeCounter();
			counter.visit(aModel);
//			System.out.printf("analyze time(sec): %.2f\n", analyseTime/1000.0);
			System.out.println("# Slice out: " + (counter.count - nc.count));
			System.out.println("# Total AST: " + counter.count);
//			System.out.println("LOC: " + StringUtil.countFile(path));
			System.out.println("===================");
			System.out.println("\n");
//			String modelname = path.substring(path.lastIndexOf("/")+1, path.length()-4);
//			Reportor rep = new Reportor(modelname, StringUtil.countFile(path), 0, 0, (counter.count - nc.count), counter.count, analyseTime/1000.0);
//			if(writeResult)
//				rep.writeToCSV("result.csv");
//			continue outloop;
		}
	}
}
