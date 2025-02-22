package ru.otus.chat.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static ru.otus.chat.server.RoleName.ADMIN;
import static ru.otus.chat.server.RoleName.USER;

public class JDBCAuthenticatedProvider implements AuthenticatedProvider {

    private List<User> users = new ArrayList<>();
    private Server server;
    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/otus-db";
    private static final String USERS_QUERY = "select * from users";
    private static final String USER_ROLE_QUERY = """
            select r.id, r."name" from roles r
            	join users_to_roles ur on r.id = ur.role_id
            	where user_id = ?
            """;
    private static final String USER_CREATE_QUERY = """
            insert into users(login, "password", username) 
            VALUES(?, ?, ?)
            """;
    private static final String IS_ADMIN_QUERY = """
            	select count(1) from roles r
             	join users_to_roles ur on r.id = ur.role_id
             	join users u on u.id = ur.user_id
             	where u.username = ? and r."name" = 'admin'
            """;

    private Connection connection;

    public JDBCAuthenticatedProvider(Server server) throws SQLException {
        this.server = server;
        this.connection = DriverManager.getConnection(DATABASE_URL, "admin", "password");
    }

    public void createUser(String login, String password, String username)  {
        try (PreparedStatement ps = connection.prepareStatement(USER_CREATE_QUERY)) {
            ps.setString(1, login);
            ps.setString(2, password);
            ps.setString(3, username);
            try (ResultSet resultSet = ps.executeQuery()) {
                ResultSet generatedKeys = ps.getGeneratedKeys();
                User user = new User(generatedKeys.getInt("id"), password, login,username);
                users.add(user);
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        System.out.println("Инициализация DbAuthenticatedProvider");
    }

    @Override
    public List<User> getUsers() {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(USERS_QUERY)) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String password = resultSet.getString("password");
                    String login = resultSet.getString("login");
                    String username = resultSet.getString("username");
                    User user = new User(id, password, login,username);
                    users.add(user);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        try (PreparedStatement prStatement = connection.prepareStatement(USER_ROLE_QUERY)) {
            for (User user : users) {
                prStatement.setInt(1, user.getId());
                List<Role> roleList = new ArrayList<>();
                try (ResultSet resultSet = prStatement.executeQuery()) {
                    while (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        String name = resultSet.getString("name");
                        Role role = new Role(id, name);
                        roleList.add(role);
                    }
                    user.setRoles(roleList);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    @Override
    public boolean isAdmin(String username) {
        int flag = 0;
        try (PreparedStatement ps = connection.prepareStatement(IS_ADMIN_QUERY)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    flag = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return flag == 1;
    }

    private String getUsernameByLoginAndPassword(String login, String password) {
        users = getUsers();
        for ( User u : users) {
            if (u.getLogin().equals(login) && u.getPassword().equals(password)) {
                return u.getUsername();
            }
        }
        return null;
    }

    @Override
    public boolean authenticate(ClientHandler clientHandler, String login, String password) {
        String authUsername = getUsernameByLoginAndPassword(login, password);
        if (authUsername == null) {
            clientHandler.sendMsg("Неверный логин/пароль");
            return false;
        }
        if (server.isUsernameBusy(authUsername)) {
            clientHandler.sendMsg("Указанная учетная запись уже занята");
            return false;
        }
        clientHandler.setUsername(authUsername);
        server.subscribe(clientHandler);
        clientHandler.sendMsg("/authok " + authUsername);
        return true;
    }

    public boolean isLoginAlreadyExists(String login) {
        for (User u : this.users) {
            if (u.getLogin().equals(login)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsernameAlreadyExists(String username) {

        for (User u : this.users) {
            if (u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean registration(ClientHandler clientHandler, String login, String password, String username) {
        if (login.length() < 3 || password.length() < 3 || username.length() < 3) {
            clientHandler.sendMsg("Логин 3+ символа, пароль 3+ символа, имя пользователя 3+ символа");
            return false;
        }
        if (isLoginAlreadyExists(login)) {
            clientHandler.sendMsg("Указанный логин уже занят");
            return false;
        }
        if (isUsernameAlreadyExists(username)) {
            clientHandler.sendMsg("Указанное имя пользователя уже занято");
            return false;
        }
        createUser(login, password, username);
        clientHandler.setUsername(username);
        server.subscribe(clientHandler);
        clientHandler.sendMsg("/regok " + username);
        return true;
    }
}
