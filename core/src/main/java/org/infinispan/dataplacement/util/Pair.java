package org.infinispan.dataplacement.util;

public class Pair<X,Y> {

	private X fst;
	private Y snd;

	public Pair(X fst, Y snd){
		this.fst = fst;
		this.snd = snd;
	}

	public X getFst() {
		return fst;
	}

	public void setFst(X fst) {
		this.fst = fst;
	}

	public Y getSnd() {
		return snd;
	}

	public void setSnd(Y snd) {
		this.snd = snd;
	}
}
