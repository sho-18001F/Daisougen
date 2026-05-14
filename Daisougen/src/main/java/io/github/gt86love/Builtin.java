package io.github.gt86love;

import java.awt.FlowLayout;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Builtin {
    
    private static JFrame currentFrame = null;

    // 登録名にクラス形式（Gui.〜）をサポート
    public static boolean isBuiltin(String name) {
        return switch (name) {
            case "puts", "kusa_print", "clear", "input", "Gui.window", "Gui.button" -> true;
            default -> false;
        };
    }

    public static void execute(String name) {
        switch (name) {
            case "puts" -> {
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++; 
                String target = App.tokens.get(App.pos++);
                if (App.variables.containsKey(target)) {
                    System.out.println(App.variables.get(target).value);
                } else {
                    if (target.startsWith("\"") && target.endsWith("\"")) {
                        target = target.substring(1, target.length() - 1);
                    }
                    System.out.println(target.replace("\\n", "\n"));
                }
                while (App.pos < App.tokens.size() && !App.tokens.get(App.pos).equals(")")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++; 
            }
            
            // クラス呼び出し形式に対応した GUI ウィンドウ生成
            case "Gui.window" -> {
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++;
                String titleToken = App.tokens.get(App.pos++);
                if (titleToken.startsWith("\"") && titleToken.endsWith("\"")) {
                    titleToken = titleToken.substring(1, titleToken.length() - 1);
                }
                while (App.pos < App.tokens.size() && !App.tokens.get(App.pos).equals(")")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++;

                final String title = titleToken;
                CountDownLatch latch = new CountDownLatch(1);

                SwingUtilities.invokeLater(() -> {
                    currentFrame = new JFrame(title);
                    currentFrame.setSize(400, 300);
                    currentFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    currentFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowClosed(java.awt.event.WindowEvent e) {
                            latch.countDown();
                        }
                    });
                    currentFrame.setLayout(new FlowLayout());
                    currentFrame.setLocationRelativeTo(null);
                    currentFrame.setVisible(true);
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // クラス呼び出し形式に対応した GUI ボタン追加
            case "Gui.button" -> {
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++;
                String labelToken = App.tokens.get(App.pos++);
                if (labelToken.startsWith("\"") && labelToken.endsWith("\"")) {
                    labelToken = labelToken.substring(1, labelToken.length() - 1);
                }
                while (App.pos < App.tokens.size() && !App.tokens.get(App.pos).equals(")")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++;

                final String label = labelToken;

                if (currentFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        JButton button = new JButton(label);
                        button.addActionListener(e -> System.out.println("ボタンが押されたｗｗｗｗ"));
                        currentFrame.add(button);
                        currentFrame.revalidate();
                        currentFrame.repaint();
                    });
                } else {
                    System.out.println("[Error] Gui.window() を先に呼び出してください！");
                }
            }
            
            case "kusa_print" -> {
                System.out.print("ｗｗｗｗｗｗｗｗｗｗｗｗ\n");
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++;
            }

            case "clear" -> {
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++;
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                    } else {
                        new ProcessBuilder("clear").inheritIO().start().waitFor();
                    }
                } catch (Exception e) {
                    System.out.print("\n\n\n\n\n\n\n\n");
                }
            }

            case "input" -> {
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals("(")) App.pos++;
                if (App.pos < App.tokens.size() && App.tokens.get(App.pos).equals(")")) App.pos++;
                java.util.Scanner scanner = new java.util.Scanner(System.in); 
                System.out.print("Input> ");
                String line = scanner.hasNextLine() ? scanner.nextLine() : "";
                System.out.println("入力された文字: " + line);
            }

            default -> System.out.println("[Error] 未実装のビルトイン関数です: " + name);
        }
    }
}
