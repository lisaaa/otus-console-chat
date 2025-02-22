package ru.otus.chat.server;

import java.util.List;

public interface AuthenticatedProvider {
    void initialize();
    boolean authenticate(ClientHandler clientHandler, String login, String password );
    boolean registration(ClientHandler clientHandler, String login, String password, String username );
    List<User> getUsers();
    boolean isAdmin(String username);

}
