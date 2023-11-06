package cn.edu.hitsz.compiler.asm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegManager {

    private final Map<String, VarAssignment> varAssignmentMap = new HashMap<>();
    private final List<VarAssignment> regMap = new ArrayList<>(7);
    private final Map<String, TargetInfo> targetInfo = new HashMap<>();
    RegTable regTable = new RegTable();

    public VarAssignment acquire1(String targetName) {
        VarAssignment varAssignment = varAssignmentMap.get(targetName);
        if (varAssignment != null) {
            return varAssignment;
        } // TODO
        return varAssignment;
    }

    public String acquire(String targetName) {

        TargetInfo assignment = targetInfo.get(targetName);
        if (assignment != null) {
            return "t%d".formatted(assignment.regLoc);
        } else {
            if (regTable.isFull()) {
                int newAssignment = regTable.replace();
            } else {
                TargetInfo newAssignment = new TargetInfo(regTable.acquire());
                targetInfo.put(targetName, newAssignment);
                return "t%d".formatted(newAssignment.regLoc);
            }

            // no free register
            throw new RuntimeException("寄存器未分配");
        }
    }

    public void release(String targetName) {
        TargetInfo assignment = targetInfo.get(targetName);
        int id = assignment.regLoc;
        if (regTable.regLRU[id] == 0) {
            throw new RuntimeException("释放未分配的寄存器");
        }
        regTable.release(id);
    }

    public static class VarAssignment {
        public boolean storeAtMem;
        public Mem mem = new Mem();
        public Reg reg = new Reg();

        public VarAssignment(int regLoc) {
            this.storeAtMem = false;
            this.reg.loc = regLoc;
            this.reg.lru = 1;
        }

        public static class Mem {
            public int loc;
        }

        public static class Reg {
            public int loc;
            public int lru;
        }
    }

    private static class RegTable {
        /**
         * t0 ~ t6 的寄存器
         * LRU 越大代表越老。
         * LRU == 0 代表未分配。
         */
        private final int[] regLRU = new int[]{0, 0, 0, 0, 0, 0, 0};

        public boolean isFull() {
            for (int lruVal : regLRU) {
                if (lruVal == 0) {
                    return false;
                }
            }
            return true;
        }

        public int acquire() {
            // find free reg
            int result = regLRU.length;
            for (int i = 0; i < regLRU.length; i++) {
                if (regLRU[i] == 0) {
                    result = i;
                    break;
                }
            }
            if (result == regLRU.length) {
                throw new RuntimeException("没有寄存器可分配");
            }
            // update LRU
            for (int i = 0; i < regLRU.length; i++) {
                if (regLRU[i] != 0) {
                    regLRU[i]++;
                }
            }
            regLRU[result] = 1;
            return result;
        }

        public void release(int idx) {
            regLRU[idx] = 0;
        }

        public int replace() {
            // find biggest LRU
            int result = regLRU.length;
            int max = 0;
            for (int i = 0; i < regLRU.length; i++) {
                if (regLRU[i] > max) {
                    result = i;
                    max = regLRU[i];
                }
            }
            if (result == regLRU.length) {
                throw new RuntimeException("好奇妙");
            }
            // update LRU
            for (int i = 0; i < regLRU.length; i++) {
                if (regLRU[i] != 0) {
                    regLRU[i]++;
                }
            }
            regLRU[result] = 1;
            return result;
        }
    }

    private static class TargetInfo {
        public boolean storeAtMem;
        public int memLoc, regLoc;

        public TargetInfo(int regLoc) {
            this.storeAtMem = false;
            this.memLoc = 0;
            this.regLoc = regLoc;
        }
    }
}
