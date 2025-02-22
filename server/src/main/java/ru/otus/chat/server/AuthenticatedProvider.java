package ru.otus.chat.server;

public interface AuthenticatedProvider {
    void initialize();
    boolean authenticate(ClientHandler clientHandler, String login, String password );
    boolean registration(ClientHandler clientHandler, String login, String password, String username );
    Role getRoleByUsername(String username);

}
