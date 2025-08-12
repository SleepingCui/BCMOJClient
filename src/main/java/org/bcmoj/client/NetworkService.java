package org.bcmoj.client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NetworkService {

    @SuppressWarnings("CallToPrintStackTrace")
    public List<String> sendAndReceive(String filePath, String jsonConfig,
                                       String serverHost, int serverPort,
                                       Consumer<Double> progressCallback) throws IOException {

        List<String> responses = new ArrayList<>();

        try (Socket socket = new Socket(serverHost, serverPort)) {
            socket.setSoTimeout(30000); // 30秒超时

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                // === 1. 文件名长度 + 文件名 ===
                File file = new File(filePath);
                if (!file.exists()) {
                    throw new FileNotFoundException("文件不存在: " + filePath);
                }

                byte[] filenameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
                out.writeInt(filenameBytes.length);
                out.write(filenameBytes);

                // === 2. 文件大小 ===
                long fileSize = file.length();
                out.writeLong(fileSize);

                // === 3. 文件内容 ===
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {

                    byte[] buffer = new byte[4096];
                    long sent = 0;
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        sent += bytesRead;
                        progressCallback.accept((double) sent / fileSize);
                    }
                }

                // === 4. JSON 配置长度 + JSON 配置 ===
                byte[] jsonBytes = jsonConfig.getBytes(StandardCharsets.UTF_8);
                out.writeInt(jsonBytes.length);
                out.write(jsonBytes);

                // === 5. Hash 长度 + Hash 字符串 ===
                String hash = computeFileHash(filePath);
                byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);
                out.writeInt(hashBytes.length);
                out.write(hashBytes);

                // === 6. 接收响应 ===
                while (true) {
                    try {
                        int responseLength = in.readInt();
                        if (responseLength == 0) {
                            break;
                        }
                        byte[] responseBytes = new byte[responseLength];
                        in.readFully(responseBytes);
                        responses.add(new String(responseBytes, StandardCharsets.UTF_8));
                    } catch (EOFException e) {
                        break;
                    }
                }

            } catch (Exception e) {
                System.err.println("发送/接收过程发生异常：");
                e.printStackTrace();
                throw e; // 抛出让上层 UI 捕获
            }

        } catch (Exception e) {
            System.err.println("连接服务器失败：" + e.getMessage());
            e.printStackTrace();
            throw e; // 抛出给调用方
        }

        return responses;
    }

    private String computeFileHash(String filePath) throws IOException {
        try (InputStream fis = Files.newInputStream(Paths.get(filePath))) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算文件哈希失败", e);
        }
    }


}
