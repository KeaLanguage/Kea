package com.kea.Parser.AST;

import com.kea.Lexer.Token;
import lombok.AllArgsConstructor;

/*
Логическая нода
 */
@AllArgsConstructor
public class LogicalNode implements Node {
    private final Node left;
    private final Node right;
    private final Token operator;

    @Override
    public void compile() {
        
    }
}
