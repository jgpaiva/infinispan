package org.infinispan.dataplacement.util;

import java.util.Collection;

public class Utils {
    public static long average(Collection<Long> col) {
        long acc = 0;
        for(long i : col) {
            acc += i;
        }
        return acc / col.size();
    }
}
