module SinglyLinkedList

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

assert NoCycles {
	AcyclicWithAtoms implies Acyclic
}

check NoCycles

run Acyclic