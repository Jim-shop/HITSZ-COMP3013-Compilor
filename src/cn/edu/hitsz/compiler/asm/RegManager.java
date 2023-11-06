package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRVariable;

import java.util.*;

public class RegManager {
    private final Map<IRVariable, VarAssignment> varAssignmentMap = new HashMap<>();
    private final VarAssignment[] regMap = new VarAssignment[7];
    private int stack_pointer = 0;

    private int countFreeReg(VarAssignment[] regMap) {
        return Arrays.stream(regMap).mapToInt(e -> e == null ? 1 : 0).sum();
    }

    private int getFreeRegIdx(VarAssignment[] regMap) {
        for (int i = 0; i < regMap.length; i++) {
            if (regMap[i] == null) {
                return i;
            }
        }
        throw new RuntimeException("没有空闲寄存器");
    }

    private int findBestRetiree(VarAssignment[] regMap) {
        int maxLru = 0, maxIdx = 0;
        for (int i = 0; i < regMap.length; i++) {
            if (regMap[i] != null) {
                if (regMap[i].reg.lru > maxLru) {
                    maxLru = regMap[i].reg.lru;
                    maxIdx = i;
                }
            }
        }
        return maxIdx;
    }

    public AllocReturn alloc(IRVariable variable) {
        List<String> appendingAsm = new ArrayList<>();
        VarAssignment varAssignment = varAssignmentMap.get(variable);
        // if currently at reg, just return
        if (varAssignment != null && !varAssignment.storeAtMem) {
            return new AllocReturn(appendingAsm, varAssignment.reg.loc);
        }
        // else we should find a place to alloc
        int freeRegCount = countFreeReg(regMap);
        if (freeRegCount == 0) {
            // if not using mem before and reg usage reach limit: transfer reg to mem
            int idx = findBestRetiree(regMap);
            VarAssignment bestRetiree = regMap[idx];
            bestRetiree.storeAtMem = true;
            bestRetiree.mem.loc = stack_pointer;
            appendingAsm.add("SW t%d, %d(x0)".formatted(bestRetiree.reg.loc, stack_pointer));
            stack_pointer += 4;
            regMap[idx] = null;
        }
        int idx = getFreeRegIdx(regMap);
        // var is not at reg, so it is either new or at mem
        if (varAssignment != null && varAssignment.storeAtMem) {
            appendingAsm.add("LW t%d, %d(x0)".formatted(idx, varAssignment.mem.loc));
        }
        if (varAssignment == null) {
            varAssignment = new VarAssignment();
            varAssignmentMap.put(variable, varAssignment);
        }
        varAssignment.storeAtMem = false;
        varAssignment.reg.lru = 0;
        varAssignment.reg.loc = idx;
        // update lru and we should go
        for (VarAssignment assignment : regMap) {
            if (assignment != null) {
                assignment.reg.lru++;
            }
        }
        regMap[idx] = varAssignment;
        return new AllocReturn(appendingAsm, varAssignment.reg.loc);
    }

    public void free(IRVariable variable) {
        VarAssignment varAssignment = varAssignmentMap.get(variable);
        assert varAssignment.storeAtMem;
        regMap[varAssignment.reg.loc] = null;
    }

    public static class AllocReturn {
        public List<String> appendingAsm;
        public String regName;

        public AllocReturn(List<String> appendingAsm, int regId) {
            this.appendingAsm = appendingAsm;
            this.regName = "t%d".formatted(regId);
        }
    }

    public static class VarAssignment {
        public boolean storeAtMem;
        public Mem mem = new Mem();
        public Reg reg = new Reg();

        public static class Mem {
            public int loc;
        }

        public static class Reg {
            public int loc;
            public int lru;
        }
    }

}
