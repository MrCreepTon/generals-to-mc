package local.generalsmc.nativebridge;

/** Bounded server-authoritative run-ahead, independent of elapsed stall debt. */
public final class NativeTurnPacer {
    public static final int MAX_LEAD=3;
    // Delivery and acknowledgement cross the network twice. This is a bounded
    // transport window, separate from the authority's three-frame execution queue.
    public static final int MAX_PEER_LEAD=18;
    private long previous;
    private double budget;
    public NativeTurnPacer(long now){previous=now;}
    public int allowance(long now,int authorityFrame,int slowestPeerFrame,int nextFrame){
        double elapsed=Math.clamp((now-previous)/1_000_000_000.,0,.25);previous=now;
        budget=Math.min(MAX_LEAD,budget+elapsed*30);
        int limit=Math.min(authorityFrame+MAX_LEAD,slowestPeerFrame+MAX_PEER_LEAD);
        return Math.max(0,Math.min((int)budget,limit-nextFrame));
    }
    public void committed(){budget=Math.max(0,budget-1);}
}
