package flack.outputdirected;

import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprVar;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.translator.A4TupleSet;
import flack.compare.AlloySolution;
import flack.compare.BugType;
import flack.compare.Difference;
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
import flack.locator.Reportor;
import flack.parser.ast.*;
import flack.parser.visitor.CollectSubExpr;
import flack.parser.visitor.SliceVisitor;
import flack.parser.visitor.WordsBinder;
import flack.utility.AlloyUtil;
import flack.utility.NodeCounter;
import flack.utility.StringUtil;
import flack.utility.VisitedNodeCounter;

import java.util.*;

public class FaultFromInstances {

	public String model;
	public A4Reporter rep;
	public CompModule world;
	Set<Exprn> visited;
	Map<Exprn, Double> node2score;
	int node_count = 0;
	int eval_count = 0;

	public FaultFromInstances(String model, A4Reporter rep, CompModule world) {
		this.model = model;
		this.rep = rep;
		this.world = world;
		this.visited = new HashSet<>();
		this.node2score = new HashMap<>();
	}

//	public void locateFaultsFromInstances(A4Solution faultyInstance, Command pred){
//
//	}

	public InstancePair genInstancePair(A4Solution faultyInstance, Command pred) throws Err {
		A4Options opt = new A4Options();

		A4Solution counterExample;
		A4Solution expectedInst;
		try {
//			A4Solution counterExample = TranslateAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), asst, opt);
			counterExample = faultyInstance;
			if (!counterExample.satisfiable())
				return new InstancePair(BugType.NO_BUG);
			expectedInst = TranslateAlloyToKodkod.execute_command_with_target(rep, world.getAllSigs(), pred, opt, counterExample);
			/* if expected instance is unsat, remove unsat core and generate new expected instance */
			if (!expectedInst.satisfiable()) {
				/* set solver to MiniSat with prover to extract unsat core when needed */
				opt.solver = A4Options.SatSolver.MiniSatProverJNI;
				A4Solution unsatSol = TranslateAlloyToKodkod.execute_command(rep, world.getAllSigs(), pred, opt);
				/*TODO: remove expr in repair_pred, though it doesn't matter, as it will always eval to true*/
				AModel sliceModel = new AModel(world, unsatSol.unsatObj());
				CompModule slicedWorld = CompUtil.parseEverything_fromString(new A4Reporter(), sliceModel.toString());
				for (Command cmd : slicedWorld.getAllCommands()) {
					if (cmd.label.equals(pred.label)) {
						expectedInst = TranslateAlloyToKodkod.execute_command_with_target(rep, slicedWorld.getAllSigs(), cmd, opt,
								counterExample);
					}
				}
				Set<Exprn> locations = new HashSet<>();
				for (Expr expr : unsatSol.unsatExpr()) {
					Exprn exprn = Exprn.parseExpr(null, expr);
					if ((boolean) expectedInst.eval(CompUtil.parseOneExpression_fromString(slicedWorld, exprn.toString())) == false) {
						locations.add(exprn);
					}
				}
				return new InstancePair(slicedWorld, counterExample, expectedInst, locations, BugType.NO_EXPECT);
			}

		} catch (Err err) {
			err.printStackTrace();
			throw err;
		}
		return new InstancePair(world, counterExample, expectedInst);
	}

	public  void extractKeywords(List<InstancePair> ips, Set<String> relatedrelations, Set<String> possiblekeywords) {
		boolean first = true;
		for (InstancePair ip : ips) {
			if (ip.bugType != BugType.NO_BUG) {
				Difference diff = ip.findDiff();
				if( diff.notInCounter.size() + diff.notInExpected.size() == 0){
					Set<String> skolems = ip.counter.skolems;
					for(String var : skolems){
						ip.difference.affectedVals.add(var);
						if(!StringUtil.isInteger(var))
							relatedrelations.add( StringUtil.findSigName(var) );
						// relatedrelations.add();
					}
				}
				relatedrelations.addAll(diff.getRelatedRelations());
				if (first) {
					possiblekeywords.addAll(diff.getPossibleKeywords());
					first = false;
				} else {
					possiblekeywords.retainAll(diff.getPossibleKeywords());
				}
			}
		}
	}

	public  int calScore(Set<String> relatedrelations, Set<String> possiblekeywords,
	                     Map<Exprn, Set<String>> node2keywords, List<InstancePair> ips){
		int collectnum = 0;
		for (Exprn node : node2keywords.keySet()) {
			node_count++;
			Set<String> words = new HashSet<>();
			words.addAll(relatedrelations);
			words.addAll(possiblekeywords);
			Set<String> keywords = new HashSet<>(node2keywords.get(node));
			keywords.retainAll(words);

			List<Exprn> suspiciousExprs = new ArrayList<>();
			if( keywords.size() > 0 ){
				suspiciousExprs.add(node);
				//evalExpr(node, ips);
				CollectSubExpr subexprs = new CollectSubExpr();
				node.accept(subexprs, null);
				//TODO: collect qt expressions
				for( Exprn e : subexprs.booleanExpr) {
					evalBoolean(e, ips);
					//analyBoolean((ExprnBinaryBool) e, ips);
					if(!visited.contains(e)) {
						analyexpr(e, ips);
						visited.add(e);
					}
				}
				if( subexprs.booleanExpr.size() == 0) {
					for (Exprn e : subexprs.relationExpr) {
						if (!visited.contains(e)) {
							if (e instanceof ExprnVar || e instanceof ExprnSig || e instanceof ExprnField)
								continue;
							analyexpr(e, ips);
							visited.add(e);
						}
					}
				}


				VisitedNodeCounter nc = new VisitedNodeCounter();
				node.accept(nc);
				collectnum += nc.count;
               /* for( Exprn e : subexprs.relationExpr) {
                    evalRelational(e, ips);
                }
                */
                /*
                for(InstancePair ip : ips){
                    instantiatedExpr(node, ip);
                }
                */
			}
		}
		return collectnum;
		// sortedMap.keySet().stream().forEach( key-> System.out.println(key + " " + String.format("%.2f", sortedMap.get(key))));
	}

	public Set<Map<String, String>> calMap(Set<String> skolems, AlloySolution sol, InstancePair ip){
		Map<String, Set<String>> skolemSig2Val = new HashMap();
		for( String skolem : skolems ){
			// add Int type skolem values
			if( StringUtil.isInteger(skolem)){
				// TODO: see ip.difference.findiff() for adding ints
				String relname = ip.difference.val2sig.get(skolem);
				if(relname == null)
					relname = "Int";
				Set<String> vals = skolemSig2Val.get(relname);
				if( vals == null )
					vals = new HashSet<>();
				vals.add(skolem);
				skolemSig2Val.put(relname, vals);
				// TODO: use this temporarily to avoid the problem of predicates with parameters, this need to be fixed
				Set<String> intvals = skolemSig2Val.get("Int");
				if( intvals == null)
					intvals = new HashSet<>();
				intvals.add(skolem);
				skolemSig2Val.put("Int", intvals);
				continue;
			}
			// keep direct sig and parent sigs
			Set<String> sigNames = new HashSet<>();
			String sigName = StringUtil.findSigName(skolem);
			sigNames.add(sigName);
			// check if there is any parent
			Sig sig = sol.getSig(sigName);
			if( sig == null) {
				continue;
			}
			sigNames.addAll( AlloyUtil.findParentsNames(sig));
			for( String name : sigNames) {
				Set<String> vals = skolemSig2Val.get(name);
				if (vals == null)
					vals = new HashSet<>();
				vals.add(skolem);
				skolemSig2Val.put(name, vals);
			}
		}

		Set<Map<String, String>> result = new HashSet<>();
		generateperm(result, skolemSig2Val, new ArrayList<>(skolemSig2Val.keySet()), 0, new HashMap());
		return result;
	}

	public void generateperm( Set<Map<String, String>> result, Map<String, Set<String>> maps, List<String> keys, int depth, Map current){
		if( depth == keys.size() ){
			result.add( new HashMap(current) );
			return;
		}
		String currentKey = keys.get(depth);
		Set<String> currentVals = maps.get(currentKey);
		for( String val : currentVals ){
			current.put(currentKey, val);
			generateperm( result, maps, keys, depth+1, current);
			current.remove(currentKey,val);
		}

	}

	public void analyexpr(Exprn expr, List<InstancePair> ips){
		CollectSubExpr sc = new CollectSubExpr();
		expr.accept(sc, null);
		double score = 0.0;
		for( InstancePair ip : ips){
//            if( ip.difference.notInExpectedSigs.size() > 0 || ip.difference.notInCounterSigs.size() > 0)
//                ;
			Set<Exprn> subs = sc.relationExpr;
			AlloySolution counter = ip.counter;
			AlloySolution expected = ip.expected;
			Set<String> ints = new HashSet<>();
			for( String s : ip.counter.skolems) {
				if(StringUtil.isInteger(s))
					ints.add(s);
			}
			Set<String> skolem = ip.difference.affectedVals;
			skolem.addAll(ints);
			Set<String> vals = new HashSet<>( ip.difference.affectedVals );
			Set<Map<String, String>> maps = calMap(skolem, ip.counter, ip);
			double mscore = 0.0;
			for (Map<String, String> m : maps) {
				Set<String> countervals = new HashSet<>();
				Set<String> expectvals = new HashSet<>();
				for( Exprn e : subs){
					eval_count++;
					String inst = e.getInstantiatedString(m);
					//System.out.println(inst);
					if( inst == null ){
						continue;
					}
					for (String used : m.values()) {
						if (inst.contains(used)) {
							countervals.add(used);
							expectvals.add(used);
						}
					}
					// TODO : problem with # operator's instantiation some times no need to instantiate
					// check to ensure the variables are legal
					// System.out.println(inst);
					if( ip.difference.notInCounterSigs.size() == 0)
						countervals.addAll( counter.evalInstantiated(inst) );
					if( ip.difference.notInExpectedSigs.size() == 0)
						expectvals.addAll( expected.evalInstantiated(inst) );
				}
				if( subs.size() == 0)
					continue;
				if( countervals.size() == 0 || expectvals.size() == 0)
					mscore += 0;
				else {
					double cscore = (double) vals.size() / countervals.size();
					double escore = (double) vals.size() / expectvals.size();
					mscore += (cscore + escore) / 2.0;
				}
			}
			if(maps.size() == 0) {
				score += mscore / 1;
			}
			else {
				score += mscore / maps.size();
			}
		}
		Double s = node2score.getOrDefault(expr, 0.0);
		//System.out.println(expr + "\t " + (s + score/ips.size()));
		node2score.put(expr, s + score / ips.size());
	}

	public void evalBoolean(Exprn expr, List<InstancePair> ips){
		for( InstancePair ip : ips) {
			if( ip.difference.notInExpectedSigs.size() > 0 || ip.difference.notInCounterSigs.size() > 0)
				continue;
			AlloySolution counter = ip.counter;
			AlloySolution expected = ip.expected;
			Set<String> skolem =  ip.difference.affectedVals; //counter.skolems == null ? ip.difference.affectedVals : counter.skolems;//
			Set<Map<String, String>> maps = new HashSet<>();
			//System.out.println(ip.difference);
			if( ip.difference.notInCounter.size() + ip.difference.notInExpected.size() == 0){
				Map<String, String> counter_map = new HashMap<>();
				for(ExprVar v : ip.counter.sol.getAllSkolems()){
					String s = v.toString();
					String name =  s.substring(s.lastIndexOf("_")+1, s.length()) ;
					try {
						A4TupleSet r = (A4TupleSet)ip.counter.sol.eval(v);
						if(r==null || r.size()==0)
							continue;
						String val = r.iterator().next().toString();
						counter_map.put(name, val);
					} catch (Err err) {
						err.printStackTrace();
					}
				}
				maps = calSkolmizedMap(ip);
				String counterinst = expr.getInstantiatedString(counter_map);
				//System.out.println(counterinst);
				Boolean counter_res = counter.evalInstantiatedBoolean(counterinst);
				if(counterinst.contains("#")){
					counter_res = false;
				}
				if(counter_res != null && !counter_res){
					for (Map<String, String> m : maps) {
						eval_count++;
						// System.out.println("expr: " + expr);
						String inst = expr.getInstantiatedString(m);
						Boolean expected_res = expected.evalInstantiatedBoolean(inst);
						if(expected_res!=null && expected_res){
							Double score = node2score.getOrDefault(expr, 0.0);
							node2score.put(expr, score + 1.0);
							if( expr.getParent() instanceof Exprn) {
								Exprn parent = (Exprn)expr.getParent();
								if( parent instanceof ExprnList) {
									continue;
								}

								Double snew = node2score.getOrDefault(parent, 0.0);
								node2score.put(parent, snew + 1.0);
							}
						}
					}
				}
				continue;
			}else
				maps = calMap(skolem, ip.counter, ip);

			for (Map<String, String> m : maps) {
				eval_count++;
				// System.out.println("expr: " + expr);
				String inst = expr.getInstantiatedString(m);

				Boolean counter_res = counter.evalInstantiatedBoolean(inst);
				Boolean expected_res = expected.evalInstantiatedBoolean(inst);
				if (counter_res != expected_res) {
					//                 System.out.println(counter_res + " not equal: " + expr + " " + inst);
//                   System.out.println(counter);
					Double score = node2score.getOrDefault(expr, 0.0);
					node2score.put(expr, score + 1.0);
					if( expr.getParent() instanceof Exprn) {
						Exprn parent = (Exprn)expr.getParent();
						if( parent instanceof ExprnList) {
							continue;
						}

						Double snew = node2score.getOrDefault(parent, 0.0);
						node2score.put(parent, snew + 1.0);
					}
				}
			}
		}
	}

	public Set<Map<String, String>> calSkolmizedMap(InstancePair ip){
		List<List<Pair<String, String>>> listOfAllVars = new ArrayList<>();
		for(ExprVar v : ip.counter.sol.getAllSkolems()){
			List<Pair<String,String>> tmpList = new ArrayList<>();
			String s = v.toString();
			String name =  s.substring(s.lastIndexOf("_")+1, s.length()) ;
			try {
				A4TupleSet r = (A4TupleSet)ip.counter.sol.eval(v);
				if( r == null || r.size() == 0)
					continue;
				String val = r.iterator().next().toString();
				if(StringUtil.isInteger(val)){
					for(int i = 0; i < 7; i++){
						tmpList.add(new Pair<>(name, String.valueOf(i)));

					}
				}else{
					tmpList.add(new Pair<>(name, val));
				}
			} catch (Err err) {
				err.printStackTrace();
			}
			listOfAllVars.add(tmpList);
		}
		Set<Map<String, String>> prev = new HashSet<>();
		for(List<Pair<String, String>> l : listOfAllVars){
			if(prev.isEmpty()){
				Map<String, String> cur = new HashMap<>();
				l.stream().forEach(p -> cur.put(p.a, p.b));
				prev.add(cur);
			}else{
				Set<Map<String, String>> current = new HashSet<>();
				for(Pair<String, String> pair : l){
					for( Map<String, String> pre : prev ){
						Map<String, String> p = new HashMap<>();
						p.putAll(pre);
						p.put(pair.a, pair.b);
						current.add(p);
					}
				}
				prev = current;
			}
		}
		return prev;
	}

	public void findFaultsFromInstancePair(InstancePair ip, Command asst, Command pred){
		// NO_BUG and NO_EXPECT are not covered in current scope of work
		AModel aModel = new AModel(world);
		Set<String> relatedrelations = new HashSet<>();
		Set<String> possiblekeywords = new HashSet<>();
		extractKeywords(List.of(ip), relatedrelations, possiblekeywords);
//                System.out.println("related: " + relatedrelations);
//                System.out.println("inferred: " + possiblekeywords);

		// slice alloy model to find related pred and func for the corresponding command
		SliceVisitor sv = new SliceVisitor(pred);
		sv.visit(aModel);
		//System.out.println("related preds: " + sv.relatedPreds);
		// collect keywords
		// WordsCollector kc = new WordsCollector(sv.relatedPreds);
		WordsBinder kc = new WordsBinder(sv.relatedPreds);
		kc.visit(aModel, null);

//                kc.node2keyword.keySet().stream().forEach( k -> {
//                   System.out.println( k +  ":\n\t " + kc.node2keyword.get(k)+"\n");
//               });
		//System.out.println(kc.node2keyword);
		// calculate priority based on difference and keywords
		//calculatePriority(relatedrelations, possiblekeywords, kc.node2keyword, ips);
//                for( Exprn e : kc.node2keyword.keySet()){
//                    System.out.println(e + " " + kc.node2keyword.get(e)+ "\n");
//                }
		int collected = calScore(relatedrelations, possiblekeywords, kc.node2keyword, List.of(ip));
		// }

		LinkedHashMap<Exprn, Double> sortedMap = new LinkedHashMap<>();
		node2score.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));

		// output stats
		//System.out.println("type c bug");
		System.out.println("RANK LIST:");
		int rank = 0;
		Set<String> vs = new HashSet<>();
		for( Exprn expr : sortedMap.keySet()){
			if(vs.contains(expr.toString())){
				continue;
			}else
				vs.add(expr.toString());
			if( expr instanceof ExprnBinaryBool){
				Exprn left = ((ExprnBinaryBool) expr).getLeft();
				Exprn right = ((ExprnBinaryBool) expr).getRight();
				Double leftscore = sortedMap.get(left);
				Double rightscore = sortedMap.get(right);
				String append = ( leftscore != rightscore)? ((ExprnBinaryBool) expr).getOp().toString() : "";
				System.out.println(rank + ": " + expr + " " +  String.format("%.2f", sortedMap.get(expr)) + " " + append);
			}else{
				System.out.println(rank + ": " + expr + " " + String.format("%.2f", sortedMap.get(expr)));
			}
			rank++;
		}
		NodeCounter counter = new NodeCounter();
		counter.visit(aModel);
		System.out.println("-------------------");
//		System.out.printf("analyze time(sec): %.2f\n", analyseTime/1000.0);
		System.out.println("# rel: " + (relatedrelations.size() + possiblekeywords.size()));
		int valnum = 0;
//		for(InstancePair ip : ips) {
//			if(ip.difference == null) {
//				//  System.out.println("type c");
//				break;
//			}
//			valnum += ip.difference.affectedVals.size();
//		}
//		System.out.println("# val: " + valnum / ips.size());
		System.out.println("# Slice Out: " + (counter.count - collected));
		System.out.println("# Total AST: " + counter.count);
//		System.out.println("LOC: " + StringUtil.countFile(path));
		System.out.println("evals: " + eval_count + " | " + "node: " + node_count);
		System.out.println("===================");
		System.out.println("\n");
//		String modelname = path.substring(path.lastIndexOf("/")+1, path.length()-4);
//		Reportor rep = new Reportor(modelname, StringUtil.countFile(path), (relatedrelations.size() + possiblekeywords.size()), valnum / ips.size(), (counter.count - collected), counter.count, analyseTime/1000.0);
//		if(writeResult)
//			rep.writeToCSV("result.csv");
	}
}
