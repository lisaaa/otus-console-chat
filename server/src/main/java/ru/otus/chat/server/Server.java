package ru.otus.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private AuthenticatedProvider authenticatedProvider;

    public Server(int port) throws SQLException {
        this.port = port;
        clients = new CopyOnWriteArrayList<>();
        authenticatedProvider = new JDBCAuthenticatedProvider(this);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту: " + port);
            authenticatedProvider.initialize();
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage("Из чата вышел: " + clientHandler.getUsername());
    }

    public void broadcastMessage(String message) {
        for (ClientHandler c : clients) {
            c.sendMsg(message);
        }
    }

    public boolean isUsernameBusy(String usedrname) {
        for (ClientHandler c : clients) {
            if (c.getUsername().equals(usedrname)) {
                return true;
            }
        }
        return false;
    }

    public AuthenticatedProvider getAuthenticatedProvider() {
        return authenticatedProvider;
    }

    public synchronized void sendMessageToClient(String message) {
        for (ClientHandler client : clients) {
            if (message.contains(client.getUsername())) {
                client.sendMsg(message);
            }
        }
    }

    public synchronized void unsubscribeClient(String message, ClientHandler clientHandler) {
       // if (ADMIN.equals(authenticatedProvider.getRoleByUsername(clientHandler.getUsername()))) {
        if (authenticatedProvider.isAdmin(clientHandler.getUsername())) {
            for (ClientHandler client : clients) {
                    if (message.split(" ")[1].equals(client.getUsername())) {
                        unsubscribe(client);
                        System.out.println(client.getUsername());
                    }
                }
        } else {
            clientHandler.sendMsg("У вас недостаточно прав для удаления пользователя из чата!");
        }
    }
}
