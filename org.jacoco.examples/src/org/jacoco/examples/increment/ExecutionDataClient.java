package org.jacoco.examples.increment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
/** * 用于生成exec文件 */
public class ExecutionDataClient {
    private static final String DESTFILE = "D:/IdeaProjects/middleware-checker/middleware-checker.exec";//导出的文件路径

    private static final String ADDRESS = "192.168.8.124";//配置的Jacoco的IP

    private static final int PORT = 6400;//Jacoco监听的端口

    public static void main(final String[] args) throws IOException {
        final FileOutputStream localFile = new FileOutputStream(DESTFILE);
        final ExecutionDataWriter localWriter = new ExecutionDataWriter(
                localFile);

        //连接Jacoco服务
        final Socket socket = new Socket(InetAddress.getByName(ADDRESS), PORT);
        final RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
        final RemoteControlReader reader = new RemoteControlReader(socket.getInputStream());
        reader.setSessionInfoVisitor(localWriter);
        reader.setExecutionDataVisitor(localWriter);

        // 发送Dump命令，获取Exec数据
        writer.visitDumpCommand(true, false);
        if (!reader.read()) {
            throw new IOException("Socket closed unexpectedly.");
        }

        socket.close();
        localFile.close();
    }

    private ExecutionDataClient() {
    }
}