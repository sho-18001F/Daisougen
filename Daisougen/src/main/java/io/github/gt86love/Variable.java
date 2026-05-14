package io.github.gt86love;

import java.util.*;
import java.math.BigInteger;

public class Variable {
    public String type;
    public Object value;

    public Variable(String type, String valStr) {
        this.type = type;
        this.value = castValue(type, valStr);
    }

    public Variable(String type, Object obj) {
        this.type = type;
        this.value = obj;
    }

    // 文字列から各型に安全に変換する処理
    private Object castValue(String type, String val) {
        if (val == null) {
            val = "";
        }
        val = val.replace("\"", "").trim();

        // 空リテラルやパース不能な状態のデフォルト値を安全に保証
        boolean isEmptyOrLiteral = val.isEmpty() || val.equals("[]") || val.equals("[:]");

        switch (type) {
            case "short":     
                return isEmptyOrLiteral ? (short) 0 : Short.parseShort(val);
            case "ushort":    
                return isEmptyOrLiteral ? 0 : Integer.parseInt(val) & 0xFFFF;
            case "int":       
                return isEmptyOrLiteral ? 0 : Integer.parseInt(val);
            case "uint":      
                return isEmptyOrLiteral ? 0L : Long.parseLong(val) & 0xFFFFFFFFL;
            case "long":      
            case "longlong":  
                return isEmptyOrLiteral ? 0L : Long.parseLong(val);
            case "ulong":     
            case "ulonglong": 
                // 符号なし64bit/巨大整数はBigIntegerで統一し、計算時の不整合を防ぐ
                return isEmptyOrLiteral ? BigInteger.ZERO : new BigInteger(val);
            case "boolean":   
                return Boolean.parseBoolean(val);
            case "list":      
                return new ArrayList<Object>();
            case "hash":      
                return new HashMap<Object, Object>();
            default:          
                return val; // stringなど
        }
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
