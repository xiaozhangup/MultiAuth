package cn.jason31416.multiauth.api;

public interface ILoginSession {
    String getUsername();

    java.util.UUID getUuid();

    String getAuthMethod();
}
