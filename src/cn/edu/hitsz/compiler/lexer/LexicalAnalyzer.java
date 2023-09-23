package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private final List<Token> tokens = new ArrayList<>();
    private List<Integer> fileContent;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // 词法分析前的缓冲区实现
        this.fileContent = FileUtils.readFile(path).codePoints().boxed().collect(Collectors.toList());
        this.fileContent.add(-1); // eof
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // 自动机实现的词法分析过程
        enum State {
            START, ID, INT_CONST
        }
        State state = State.START;
        StringBuilder stringBuilder = new StringBuilder();
        for (int pos = 0; pos < fileContent.size(); ) {
            int c = fileContent.get(pos);
            switch (state) {
                case START -> {
                    stringBuilder.setLength(0);
                    if (Character.isLetter(c) || c == '_') {
                        stringBuilder.appendCodePoint(c);
                        state = State.ID;
                    } else if (Character.isDigit(c)) {
                        stringBuilder.appendCodePoint(c);
                        state = State.INT_CONST;
                    } else if (c == '=') {
                        tokens.add(Token.simple("="));
                    } else if (c == ',') {
                        tokens.add(Token.simple(","));
                    } else if (c == ';') {
                        tokens.add(Token.simple("Semicolon"));
                    } else if (c == '+') {
                        tokens.add(Token.simple("+"));
                    } else if (c == '-') {
                        tokens.add(Token.simple("-"));
                    } else if (c == '*') {
                        tokens.add(Token.simple("*"));
                    } else if (c == '/') {
                        tokens.add(Token.simple("/"));
                    } else if (c == '(') {
                        tokens.add(Token.simple("("));
                    } else if (c == ')') {
                        tokens.add(Token.simple(")"));
                    } else if (c == -1) {
                        tokens.add(Token.eof());
                    } else if (!Character.isWhitespace(c)) { // unexpected character
                        System.out.println("pos: " + pos + " char: " + c);
                        throw new NotImplementedException();
                    }
                    pos++;
                }
                case ID -> {
                    if (Character.isLetterOrDigit(c)) {
                        stringBuilder.appendCodePoint(c);
                        pos++;
                    } else {
                        String id = stringBuilder.toString();
                        if (TokenKind.isAllowed(id)) {
                            tokens.add(Token.simple(id));
                        } else {
                            tokens.add(Token.normal("id", id));
                            if (!symbolTable.has(id)) {
                                symbolTable.add(id);
                            }
                        }
                        state = State.START;
                    }
                }
                case INT_CONST -> {
                    if (Character.isDigit(c)) {
                        stringBuilder.appendCodePoint(c);
                        pos++;
                    } else {
                        String digit = stringBuilder.toString();
                        tokens.add(Token.normal("IntConst", digit));
                        state = State.START;
                    }
                }
            }
        }
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // 从词法分析过程中获取 Token 列表
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(path, StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList());
    }


}
