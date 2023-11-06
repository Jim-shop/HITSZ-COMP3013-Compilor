package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.*;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    List<String> asm = new ArrayList<>();
    private List<Instruction> irTable;
    private HashMap<String, VarUsageInfo> varUsageInfo;

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        // 读入前端提供的中间代码并生成所需要的信息
        this.irTable = renaming(trimIrForRv(originInstructions));
        this.varUsageInfo = getVarUsageInfo(this.irTable);
    }

    private List<Instruction> trimIrForRv(List<Instruction> instructions) {
        List<Instruction> result = new ArrayList<>();
        for (Instruction instruction : instructions) {
            InstructionKind kind = instruction.getKind();
            if (kind.isBinary()) {
                IRValue lhs = instruction.getLHS(), rhs = instruction.getRHS();
                if (lhs.isIRVariable() && rhs.isIRVariable()) { // (var, var)
                    result.add(instruction);
                } else if (lhs.isImmediate() && rhs.isImmediate()) { // (imm, imm)
                    int lhsVal = ((IRImmediate) lhs).getValue(), rhsVal = ((IRImmediate) rhs).getValue();
                    int calc = switch (kind) {
                        case ADD -> lhsVal + rhsVal;
                        case SUB -> lhsVal - rhsVal;
                        case MUL -> lhsVal * rhsVal;
                        default -> throw new NotImplementedException();
                    };
                    result.add(Instruction.createMov(instruction.getResult(), IRImmediate.of(calc)));
                } else if (lhs.isImmediate() && rhs.isIRVariable()) { // (imm, var)
                    if (kind == InstructionKind.ADD) {
                        result.add(Instruction.createAdd(instruction.getResult(), rhs, lhs));
                    } else { // SUB, MUL
                        result.add(Instruction.createMov(instruction.getResult(), lhs));
                        if (kind == InstructionKind.SUB) {
                            result.add(Instruction.createSub(instruction.getResult(), instruction.getResult(), rhs));
                        } else { // MUL
                            result.add(Instruction.createMul(instruction.getResult(), instruction.getResult(), rhs));
                        }
                    }
                } else { // (var, imm)
                    if (kind == InstructionKind.MUL) {
                        result.add(Instruction.createMov(instruction.getResult(), instruction.getRHS()));
                        result.add(Instruction.createMul(instruction.getResult(), instruction.getResult(), instruction.getLHS()));
                    } else { // ADD, SUB
                        result.add(instruction);
                    }
                }
            } else if (kind.isUnary()) { // MOV
                result.add(instruction);
            } else { // RET
                result.add(instruction);
                break;
            }
        }
        return result;
    }

    private List<Instruction> renaming(List<Instruction> instructions) {
        List<Instruction> result = new ArrayList<>();
        RenamingTranslator translator = new RenamingTranslator();
        for (Instruction instruction : instructions) {
            switch (instruction.getKind()) {
                case MOV -> { // var <- (imm) / (var)
                    IRValue newFrom = translator.getTranslatedValue(instruction.getFrom());
                    IRVariable newTarget = translator.setNewName(instruction.getResult());
                    result.add(Instruction.createMov(newTarget, newFrom));
                }
                case MUL -> { // var <- (var, var)
                    IRValue newLhs = translator.getTranslatedValue(instruction.getLHS());
                    IRValue newRhs = translator.getTranslatedValue(instruction.getRHS());
                    IRVariable newTarget = translator.setNewName(instruction.getResult());
                    result.add(Instruction.createMul(newTarget, newLhs, newRhs));
                }
                case ADD -> { // var <- (var, var)
                    IRValue newLhs = translator.getTranslatedValue(instruction.getLHS());
                    IRValue newRhs = translator.getTranslatedValue(instruction.getRHS());
                    IRVariable newTarget = translator.setNewName(instruction.getResult());
                    result.add(Instruction.createAdd(newTarget, newLhs, newRhs));
                }
                case SUB -> {
                    IRValue newLhs = translator.getTranslatedValue(instruction.getLHS());
                    IRValue newRhs = translator.getTranslatedValue(instruction.getRHS());
                    IRVariable newTarget = translator.setNewName(instruction.getResult());
                    result.add(Instruction.createSub(newTarget, newLhs, newRhs));
                }
                case RET -> {
                    IRValue newRet = translator.getTranslatedValue(instruction.getReturnValue());
                    result.add(Instruction.createRet(newRet));
                }
            }
        }
        return result;
    }

    private HashMap<String, VarUsageInfo> getVarUsageInfo(List<Instruction> instructions) {
        HashMap<String, VarUsageInfo> varUsageInfo = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            switch (instruction.getKind()) {
                case MOV -> {
                    // update target usage info
                    String targetName = instruction.getResult().getName();
                    VarUsageInfo targetInfo = varUsageInfo.get(targetName);
                    if (targetInfo != null) {
                        targetInfo.end = i;
                    } else {
                        varUsageInfo.put(targetName, new VarUsageInfo(i, i));
                    }
                    // update source usage info
                    IRValue from = instruction.getFrom();
                    if (from.isIRVariable()) {
                        String sourceName = ((IRVariable) from).getName();
                        VarUsageInfo sourceInfo = varUsageInfo.get(sourceName);
                        if (sourceInfo == null) {
                            throw new RuntimeException("使用未初始化的变量%s".formatted(sourceName));
                        }
                        sourceInfo.end = i;
                    }
                }
                case ADD, SUB, MUL -> {
                    // update target usage info
                    String targetName = instruction.getResult().getName();
                    VarUsageInfo targetInfo = varUsageInfo.get(targetName);
                    if (targetInfo != null) {
                        targetInfo.end = i;
                    } else {
                        varUsageInfo.put(targetName, new VarUsageInfo(i, i));
                    }
                    // update lhs usage info
                    IRValue lhs = instruction.getLHS();
                    if (lhs.isIRVariable()) {
                        String lhsName = ((IRVariable) lhs).getName();
                        VarUsageInfo lhsInfo = varUsageInfo.get(lhsName);
                        if (lhsInfo == null) {
                            throw new RuntimeException("使用未初始化的变量%s".formatted(lhsName));
                        }
                        lhsInfo.end = i;
                    }
                    // update rhs usage info
                    IRValue rhs = instruction.getRHS();
                    if (rhs.isIRVariable()) {
                        String rhsName = ((IRVariable) rhs).getName();
                        VarUsageInfo rhsInfo = varUsageInfo.get(rhsName);
                        if (rhsInfo == null) {
                            throw new RuntimeException("使用未初始化的变量%s".formatted(rhsName));
                        }
                        rhsInfo.end = i;
                    }
                }
                case RET -> {
                    // update target usage info
                    IRValue ret = instruction.getReturnValue();
                    if (ret.isIRVariable()) {
                        String retName = ((IRVariable) ret).getName();
                        VarUsageInfo retInfo = varUsageInfo.get(retName);
                        if (retInfo == null) {
                            throw new RuntimeException("使用未初始化的变量%s".formatted(retName));
                        }
                        retInfo.end = i;
                    }
                }
            }
        }
        return varUsageInfo;
    }

    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // 执行寄存器分配与代码生成
        RegManager regManager = new RegManager();
        for (int i = 0; i < irTable.size(); i++) {
            Instruction instruction = irTable.get(i);
            switch (instruction.getKind()) {
                case MOV -> {
                    // assign target register
                    String targetName = instruction.getResult().getName();
                    String targetAssignment = regManager.acquire(targetName);
                    // instruction emit
                    IRValue source = instruction.getFrom();
                    if (source.isImmediate()) {
                        asm.add("LI %s, %d".formatted(targetAssignment, ((IRImmediate) source).getValue()));
                    } else {
                        String sourceName = ((IRVariable) source).getName();
                        VarUsageInfo sourceInfo = varUsageInfo.get(sourceName);
                        String sourceAssignment = regManager.acquire(sourceName);
                        asm.add("MV %s, %s".formatted(targetAssignment, sourceAssignment));
                        // clear assignment
                        if (sourceInfo.end == i) {
                            regManager.release(sourceName);
                        }
                    }
                }
                case ADD, SUB, MUL -> {
                    // assign target register
                    String targetName = instruction.getResult().getName();
                    String targetAssignment = regManager.acquire(targetName);
                    // instruction emit
                    assert instruction.getLHS().isIRVariable();
                    IRVariable lhs = (IRVariable) instruction.getLHS();
                    IRValue rhs = instruction.getRHS();
                    VarUsageInfo lhsInfo = varUsageInfo.get(lhs.getName());
                    String lhsAssignment = regManager.acquire(lhs.getName());
                    if (rhs.isImmediate()) {
                        asm.add(switch (instruction.getKind()) {
                            case ADD ->
                                    "ADDI %s, %s, %d".formatted(targetAssignment, lhsAssignment, ((IRImmediate) rhs).getValue());
                            case SUB ->
                                    "SUBI %s, %s, %d".formatted(targetAssignment, lhsAssignment, ((IRImmediate) rhs).getValue());
                            case MUL -> throw new NotImplementedException();
                            default -> throw new IllegalStateException("Unexpected value: " + instruction.getKind());
                        });
                    } else {
                        String rhsName = ((IRVariable) rhs).getName();
                        VarUsageInfo rhsInfo = varUsageInfo.get(rhsName);
                        String rhsAssignment = regManager.acquire(rhsName);
                        asm.add(switch (instruction.getKind()) {
                            case ADD -> "ADD %s, %s, %s".formatted(targetAssignment, lhsAssignment, rhsAssignment);
                            case SUB -> "SUB %s, %s, %s".formatted(targetAssignment, lhsAssignment, rhsAssignment);
                            case MUL -> "MUL %s, %s, %s".formatted(targetAssignment, lhsAssignment, rhsAssignment);
                            default -> throw new IllegalStateException("Unexpected value: " + instruction.getKind());
                        });
                        // clear assignment
                        if (rhsInfo.end == i) {
                            regManager.release(rhsName);
                        }
                    }
                    if (lhsInfo.end == i) {
                        regManager.release(lhs.getName());
                    }
                }
                case RET -> {
                    IRValue returnValue = instruction.getReturnValue();
                    if (returnValue.isImmediate()) {
                        asm.add("LI a0, %d".formatted(((IRImmediate) returnValue).getValue()));
                    } else {
                        String returnValueAssignment = regManager.acquire(((IRVariable) returnValue).getName());
                        asm.add("MV a0, %s".formatted(returnValueAssignment));
                    }
                }
            }
        }
    }

    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // 输出汇编代码到文件
        FileUtils.writeLines(path, asm);
    }

    private static class VarUsageInfo {
        public int start, end;

        public VarUsageInfo(int start) {
            this.start = start;
        }

        public VarUsageInfo(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "VarUsageInfo{" + "start=" + start + ", end=" + end + '}';
        }
    }

    static class RenamingTranslator {
        private final Map<String, String> renamingTable = new HashMap<>();

        public IRVariable getRenamedVariable(IRVariable variable) {
            String name = variable.getName();
            String newName = renamingTable.getOrDefault(name, name);
            return IRVariable.named(newName);
        }

        public IRValue getTranslatedValue(IRValue value) {
            if (value.isImmediate()) {
                return value;
            } else {
                return getRenamedVariable((IRVariable) value);
            }
        }

        public IRVariable setNewName(IRVariable variable) {
            String originName = variable.getName();
            if (renamingTable.containsKey(originName)) {
                String oldName = renamingTable.get(originName);
                String newName = "%s+".formatted(oldName);
                renamingTable.put(originName, newName);
            } else {
                renamingTable.put(originName, originName);
            }
            return getRenamedVariable(variable);
        }
    }
}

