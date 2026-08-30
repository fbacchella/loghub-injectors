package fr.loghub.core.publishers;

public interface Publisher {

    void close();

    boolean send(byte[] content);

}
