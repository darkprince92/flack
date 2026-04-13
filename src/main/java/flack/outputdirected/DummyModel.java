package flack.outputdirected;

public class DummyModel {
	public static String getAlloyCode() {
		StringBuilder alloyCode = new StringBuilder();
		/*
		one sig List {
		  header : lone Node
		}

		sig Node {
		  link: lone Node
		}

		fact {
			Node in List.header.*link
			// some l: link | l not in ~link
		}

		pred Acyclic {
		  no List.header or all n: Node | n not in n.^link
		}

		pred faultyAcyclic {
		  no List.header or all n: Node | n not in n.link
		}

		pred AcyclicWithAtoms {
			some disj n1, n2: Node {
				header = List -> n1
				link = n1 -> n2 + n2 -> n1
			}
		}
		 */
		alloyCode.append("one sig List {")
				.append("\n  header : lone Node")
				.append("\n}")
				.append("\n\nsig Node {")
				.append("\n  link: lone Node")
				.append("\n}")
				.append("\n\nfact {")
				.append("\n\tNode in List.header.*link")
				// .append("\n\tsome l: link | l not in ~link")
				.append("\n}")
				.append("\n\npred Acyclic {")
				.append("\n  no List.header or all n: Node | n not in n.^link")
				.append("\n}")
				.append("\n\npred faultyAcyclic {")
				.append("\n  no List.header or all n: Node | n not in n.link")
				.append("\n}")
				.append("\n\npred AcyclicWithAtoms {")
				.append("\n\tsome disj n1, n2: Node {")
				.append("\n\theader = List -> n1")
				.append("\n\tlink = n1 -> n2 + n2 -> n1")
				.append("\n\t}")
				.append("\n}");

		/*
		run FaultyWithAtoms {
			AcyclicWithAtoms
			faultyAcyclic
		}

		run NotFaultyWithAtoms {
			Acyclic
		}
		 */
		alloyCode.append("\n\n")
				.append("run FaultyWithAtoms {")
				.append("\n\tAcyclicWithAtoms")
				.append("\n\tfaultyAcyclic")
				.append("\n}")
				.append("\n\nrun NotFaultyWithAtoms {")
				.append("\n\tAcyclic")
				.append("\n}");

		return alloyCode.toString();
	}
}
