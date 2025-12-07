package bj.gouv.sgg.util;

import bj.gouv.sgg.config.LawProperties;

public class MachineCapacityUtil {
    private MachineCapacityUtil() {}
    public static boolean isIaCapable(LawProperties props) {
        int required = props.getCapacity().getIa();
        return Runtime.getRuntime().availableProcessors() >= Math.max(1, required);
    }
    public static boolean isOcrCapable(LawProperties props) {
        int required = props.getCapacity().getOcr();
        return Runtime.getRuntime().availableProcessors() >= Math.max(1, required);
    }
}
