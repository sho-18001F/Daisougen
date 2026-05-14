package io.github.gt86love;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
    public static List<String> tokens = new ArrayList<>();
    public static Map<String, Variable> variables = new HashMap<>(); 
    public static Map<String, Integer> functions = new HashMap<>(); 
    public static Map<String, String> functionParams = new HashMap<>(); 
    public static Stack<Integer> callStack = new Stack<>(); 
    public static int pos = 0;

    private static final Set<String> TYPES = new HashSet<>(java.util.Arrays.asList(
        "string", "int", "short", "long", "longlong", "uint", "ushort", "ulong", "ulonglong", "boolean", "list", "hash"
    ));

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("[Error] 実行するファイルを指定してください！ｗ");
            return;
        }

        String mainFilePath = null;
        List<String> linkTargets = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--link:")) {
                linkTargets.add(arg.substring(7));
            } else {
                mainFilePath = arg;
            }
        }

        if (mainFilePath == null) {
            System.out.println("[Error] メインの実行ファイル（.kusa）が指定されていません！");
            return;
        }

        for (String target : linkTargets) {
            String linkContent = fetchCode(target);
            if (!linkContent.isEmpty()) {
                tokenize(linkContent, tokens);
            }
        }

        String sourceCode = "";
        try {
            if (Files.exists(Paths.get(mainFilePath))) {
                sourceCode = Files.readString(Paths.get(mainFilePath));
            } else {
                System.out.println("[Error] メインファイルが見つかりません: " + mainFilePath);
                return;
            }
        } catch (IOException e) {
            System.out.println("[Error] メインファイル読み込み失敗: " + e.getMessage());
            return;
        }

        tokenize(sourceCode, tokens);
        resolveIncludes();

        // 【事前スキャン】
        pos = 0;
        while (pos < tokens.size()) {
            if (tokens.get(pos).equals("fn")) {
                pos++; 
                String funcName = tokens.get(pos++); 
                pos++; 
                String paramName = "";
                if (!tokens.get(pos).equals(")")) paramName = tokens.get(pos++);
                pos++; pos++; 
                functions.put(funcName, pos); 
                functionParams.put(funcName, paramName); 
                
                int depth = 1;
                while (depth > 0 && pos < tokens.size()) {
                    String t = tokens.get(pos++);
                    if (t.equals("{")) depth++;
                    if (t.equals("}")) depth--;
                }
            } else {
                pos++;
            }
        }

        pos = 0;
        runBlock();
    }

    private static void tokenize(String input, List<String> targetList) {
        // 右シフト >> や比較演算子がバラバラに分解されないように保護するトークナイザー
        String prepared = input
            .replaceAll(">>", " __RSHIFT__ ")
            .replaceAll("==", " __EQ__ ")
            .replaceAll("!=", " __NE__ ")
            .replaceAll(">=", " __GE__ ")
            .replaceAll("<=", " __LE__ ");

        Pattern pattern = Pattern.compile("\"[^\"]*\"|==|!=|>=|<=|[(){}=.+\\-<>!*/]|[^\\s(){}=.+\\-<>!*/]+");
        Matcher matcher = pattern.matcher(prepared);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.equals("__RSHIFT__")) token = ">>";
            else if (token.equals("__EQ__")) token = "==";
            else if (token.equals("__NE__")) token = "!=";
            else if (token.equals("__GE__")) token = ">=";
            else if (token.equals("__LE__")) token = "<=";
            targetList.add(token);
        }
    }

    private static String fetchCode(String pathOrUrl) {
        try {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(pathOrUrl)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    System.out.println("[Error] Webリンクコードのダウンロード失敗 (Status: " + response.statusCode() + "): " + pathOrUrl);
                }
            } else {
                if (Files.exists(Paths.get(pathOrUrl))) {
                    return Files.readString(Paths.get(pathOrUrl));
                } else {
                    System.out.println("[Error] リンク指定されたファイルが存在しません: " + pathOrUrl);
                }
            }
        } catch (Exception e) {
            System.out.println("[Error] コードのフェッチ中にエラー: " + e.getMessage());
        }
        return "";
    }

    private static void resolveIncludes() {
        while (pos < tokens.size()) {
            if (tokens.get(pos).equals("include")) {
                tokens.remove(pos); 
                if (pos >= tokens.size()) break;
                String targetPath = tokens.remove(pos).replace("\"", ""); 
                
                String fileContent = fetchCode(targetPath);
                if (!fileContent.isEmpty()) {
                    List<String> newTokens = new ArrayList<>();
                    tokenize(fileContent, newTokens);
                    tokens.addAll(pos, newTokens);
                }
            } else {
                pos++;
            }
        }
    }

    private static boolean evaluateCondition(Object leftVal, String op, Object rightVal) {
        String s1 = String.valueOf(leftVal).replace("\"", "");
        String s2 = String.valueOf(rightVal).replace("\"", "");

        try {
            double n1 = Double.parseDouble(s1);
            double n2 = Double.parseDouble(s2);

            return switch (op) {
                case "==", "=" -> n1 == n2;
                case "!=" -> n1 != n2;
                case ">"  -> n1 > n2;
                case "<"  -> n1 < n2;
                case ">=" -> n1 >= n2;
                case "<=" -> n1 <= n2;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==", "=" -> s1.equals(s2);
                case "!=" -> !s1.equals(s2);
                case ">"  -> s1.compareTo(s2) > 0;
                case "<"  -> s1.compareTo(s2) < 0;
                case ">=" -> s1.compareTo(s2) >= 0;
                case "<=" -> s1.compareTo(s2) <= 0;
                default -> false;
            };
        }
    }

    // 四則演算と右シフトの優先順位を考慮して無限に連続計算する数式解析エンジン
    private static Object parseExpression() {
        List<Object> exprTokens = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        // 1. まず現在の数式ブロック（演算子が続く限り）を一気に先読みしてリスト化する
        String first = tokens.get(pos++);
        exprTokens.add(variables.containsKey(first) ? variables.get(first).value : first);

        while (pos < tokens.size() && (tokens.get(pos).equals("+") || tokens.get(pos).equals("-") || 
                                       tokens.get(pos).equals("*") || tokens.get(pos).equals("/") || 
                                       tokens.get(pos).equals(">>"))) {
            operators.add(tokens.get(pos++));
            String nextToken = tokens.get(pos++);
            exprTokens.add(variables.containsKey(nextToken) ? variables.get(nextToken).value : nextToken);
        }

        // 2. 優先度の高い演算子（*, /, >>）を左から順番に先に計算してまとめる
        for (int i = 0; i < operators.size(); i++) {
            String op = operators.get(i);
            if (op.equals("*") || op.equals("/") || op.equals(">>")) {
                Object left = exprTokens.get(i);
                Object right = exprTokens.get(i + 1);
                Object res = 0L;
                try {
                    long n1 = Long.parseLong(String.valueOf(left).replace("\"", ""));
                    long n2 = Long.parseLong(String.valueOf(right).replace("\"", ""));
                    if (op.equals("*")) res = n1 * n2;
                    else if (op.equals("/")) res = n1 / n2;
                    else res = n1 >> n2;
                } catch (Exception e) {
                    res = String.valueOf(left).replace("\"", "") + String.valueOf(right).replace("\"", "");
                }
                exprTokens.set(i, res);
                exprTokens.remove(i + 1);
                operators.remove(i);
                i--; // 要素が詰まったのでインデックスを戻す
            }
        }

        // 3. 残った優先度の低い演算子（+, -）を左から順番に計算する
        Object currentVal = exprTokens.get(0);
        for (int i = 0; i < operators.size(); i++) {
            String op = operators.get(i);
            Object nextVal = exprTokens.get(i + 1);
            try {
                long num1 = Long.parseLong(String.valueOf(currentVal).replace("\"", ""));
                long num2 = Long.parseLong(String.valueOf(nextVal).replace("\"", ""));
                currentVal = op.equals("+") ? (num1 + num2) : (num1 - num2);
            } catch (Exception e) {
                currentVal = String.valueOf(currentVal).replace("\"", "") + String.valueOf(nextVal).replace("\"", "");
            }
        }
        return currentVal;
    }

    @SuppressWarnings("unchecked")
    public static void runBlock() {
        while (pos < tokens.size()) {
            String now = tokens.get(pos++);

            if (now.equals("}")) {
                if (!callStack.isEmpty()) pos = callStack.pop();
                break; 
            }

            if (now.equals("fn")) {
                pos++; pos++; 
                if (!tokens.get(pos).equals(")")) pos++;
                pos++; 
                while (!tokens.get(pos++).equals("{"));
                int depth = 1;
                while (depth > 0 && pos < tokens.size()) {
                    String t = tokens.get(pos++);
                    if (t.equals("{")) depth++;
                    if (t.equals("}")) depth--;
                }
                continue;
            }

            if (functions.containsKey(now)) {
                pos++; 
                String argToken = tokens.get(pos++); 
                pos++; 
                Object argVal = variables.containsKey(argToken) ? variables.get(argToken).value : argToken;
                String paramName = functionParams.get(now);
                if (!paramName.isEmpty()) {
                    variables.put(paramName, new Variable("string", argVal));
                }
                callStack.push(pos); 
                pos = functions.get(now); 
                continue;
            }

            if (now.equals("while")) {
                if (pos < tokens.size() && tokens.get(pos).equals("(")) pos++;

                String leftName = tokens.get(pos++);
                String op = tokens.get(pos++);
                String rightName = tokens.get(pos++);
                
                if (pos < tokens.size() && tokens.get(pos).equals(")")) pos++;
                pos++; 

                int blockStart = pos; 

                while (true) {
                    Object leftVal = variables.containsKey(leftName) ? variables.get(leftName).value : leftName;
                    Object rightVal = variables.containsKey(rightName) ? variables.get(rightName).value : rightName;
                    
                    boolean condition = evaluateCondition(leftVal, op, rightVal);

                    if (condition) {
                        pos = blockStart;
                        runBlock();
                    } else {
                        pos = blockStart;
                        int depth = 1;
                        while (depth > 0 && pos < tokens.size()) {
                            String t = tokens.get(pos++);
                            if (t.equals("{")) depth++;
                            if (t.equals("}")) depth--;
                        }
                        break;
                    }
                }
                continue;
            }

            if (now.equals("for")) {
                if (pos < tokens.size() && tokens.get(pos).equals("(")) pos++;
                
                String countStr = tokens.get(pos++);
                int totalCount = variables.containsKey(countStr) ? Integer.parseInt(String.valueOf(variables.get(countStr).value)) : Integer.parseInt(countStr);
                
                if (pos < tokens.size() && tokens.get(pos).equals(")")) pos++;
                pos++; 
                
                int blockStart = pos;
                
                for (int i = 0; i < totalCount; i++) {
                    pos = blockStart;
                    runBlock();
                }
                pos = blockStart;
                int depth = 1;
                while (depth > 0 && pos < tokens.size()) {
                    String t = tokens.get(pos++);
                    if (t.equals("{")) depth++;
                    if (t.equals("}")) depth--;
                }
                continue;
            }

            String targetName = now;
            String methodName = "";

            if (pos < tokens.size() && tokens.get(pos).equals(".")) {
                pos++; 
                methodName = tokens.get(pos++); 
                targetName = targetName + "." + methodName;
            }

            if (Builtin.isBuiltin(targetName)) {
                Builtin.execute(targetName);
                continue;
            }

            if (now.equals("if")) {
                if (pos < tokens.size() && tokens.get(pos).equals("(")) pos++;

                String leftName = tokens.get(pos++);  
                String op = tokens.get(pos++);        
                String rightName = tokens.get(pos++); 
                
                if (pos < tokens.size() && tokens.get(pos).equals(")")) pos++;
                pos++; 

                Object leftVal = variables.containsKey(leftName) ? variables.get(leftName).value : leftName;
                Object rightVal = variables.containsKey(rightName) ? variables.get(rightName).value : rightName;
                
                boolean condition = evaluateCondition(leftVal, op, rightVal);

                if (condition) {
                    runBlock(); 
                } else {
                    int depth = 1;
                    while (depth > 0 && pos < tokens.size()) {
                        String t = tokens.get(pos++);
                        if (t.equals("{")) depth++;
                        if (t.equals("}")) depth--;
                    }
                }
                continue;
            } 

            if (TYPES.contains(now)) {
                String type = now; 
                String varName = tokens.get(pos++); 
                pos++; // "="
                
                Object finalVal = parseExpression(); 

                if (type.equals("list")) {
                    variables.put(varName, new Variable(type, new ArrayList<>()));
                } else if (type.equals("hash")) {
                    variables.put(varName, new Variable(type, new HashMap<>()));
                } else {
                    variables.put(varName, new Variable(type, String.valueOf(finalVal)));
                }
                continue;
            }

            if (variables.containsKey(now) && "list".equals(variables.get(now).type) && pos < tokens.size() && tokens.get(pos).equals(".")) {
                pos++; 
                String method = tokens.get(pos++); 
                pos++; 
                String valToken = tokens.get(pos++);
                pos++; 
                Object pushVal = variables.containsKey(valToken) ? variables.get(valToken).value : valToken.replace("\"", "");
                ((ArrayList<Object>) variables.get(now).value).add(pushVal);
                continue;
            }

            if (variables.containsKey(now) && "hash".equals(variables.get(now).type) && pos < tokens.size() && tokens.get(pos).equals(".")) {
                pos++; 
                String method = tokens.get(pos++); 
                pos++; 
                String keyToken = tokens.get(pos++);
                String valToken = tokens.get(pos++);
                pos++; 
                Object keyVal = variables.containsKey(keyToken) ? variables.get(keyToken).value : keyToken.replace("\"", "");
                Object putVal = variables.containsKey(valToken) ? variables.get(valToken).value : valToken.replace("\"", "");
                ((HashMap<Object, Object>) variables.get(now).value).put(keyVal, putVal);
                continue;
            }

            if (variables.containsKey(now) && pos < tokens.size() && tokens.get(pos).equals("=")) {
                String varName = now;
                pos++; // "="
                
                Object finalVal = parseExpression(); 
                
                variables.put(varName, new Variable(variables.get(varName).type, String.valueOf(finalVal)));
                continue;
            }
        }
    }
}
