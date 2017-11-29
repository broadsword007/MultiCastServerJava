/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testmulticast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 *
 * @author ferhan
 */
public class TestMultiCast {

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
// TODO code application logic here
ServerHandler h= new ServerHandler();
h.startOperation();
    }
    
}
class ServerHandler
{
    ClientsListener listeningThread; // listens for clients on multicast channel
    Vector<Socket> listOfClients ;
    Vector<Message> outgoingMessageQueue;
    GlobalSenderThread senderThread ;
    SimpleHttpServer httpServer;
    Vector<Message> listOfAllMessages ;
    ServerHandler() throws IOException
    {
        listOfClients = new Vector<Socket>();
        outgoingMessageQueue = new Vector<Message>();
        listOfAllMessages = new Vector<Message>();
        listeningThread = new ClientsListener(listOfClients, outgoingMessageQueue, listOfAllMessages);
        senderThread = new GlobalSenderThread(listOfClients, outgoingMessageQueue);
        httpServer = new SimpleHttpServer(listOfAllMessages);
    }
    public void startOperation()
    {
        listeningThread.start();
        senderThread.start();
        httpServer.start();
    }
}
class ClientsListener extends Thread
{
    protected byte[] buf = new byte[256];
    MulticastSocket listeningSocket;
    Vector<Message> outgoingMessageQueue;
    Vector<Socket> listOfClients;
    Vector<Message> listOfAllMessages ;
    public ClientsListener(Vector<Socket> listOfClientsVal, Vector<Message> outgoingMessageQueueVal, Vector<Message> listOfAllMessagesVal) throws IOException 
    {
        listeningSocket = new MulticastSocket(4446);
        outgoingMessageQueue = outgoingMessageQueueVal;
        listOfAllMessages = listOfAllMessagesVal;
        listOfClients = listOfClientsVal;
    }
    @Override
    public void run()
    {
        try
        {
            InetAddress group = InetAddress.getByName("230.0.0.0");
            listeningSocket.joinGroup(group);
            System.out.println("Waiting for clients");
            while (true) 
            {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                listeningSocket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                SocketAddress sender = packet.getSocketAddress();
                System.out.println("Message recieved from : "+sender+", Message : "+message);
                Socket client = new Socket(packet.getAddress(), 10001);
                if(client.isConnected())
                {
                    listOfClients.add(client);
                    System.out.println("Just connected to " + client.getRemoteSocketAddress());
                    ClientRecieverThread recieverThread = new ClientRecieverThread(client, listOfClients, outgoingMessageQueue, listOfAllMessages);
                    recieverThread.start();
                }
                else
                {
                    System.out.println("Unable to connect to " + client.getRemoteSocketAddress());
                }
                    }
                }
                catch (IOException ex) 
                {
                    Logger.getLogger(ClientsListener.class.getName()).log(Level.SEVERE, null, ex);
                }
    }
    
}
class GlobalSenderThread extends Thread
{
    Vector<Message> outgoingMessageQueue;
    Vector<Socket> listOfClients ;
    GlobalSenderThread(Vector<Socket> listOfClientsVal, Vector<Message> outgoingMessageQueueVal)
    {
        listOfClients = listOfClientsVal;
        outgoingMessageQueue = outgoingMessageQueueVal;
    }
    @Override
    public void run()
    {
        OutputStream outToServer;
        try 
        {
            while(true)
            {
        if(!outgoingMessageQueue.isEmpty())
        {
            //send message at 0 to all clients and remove 0th index
            String messageToSend = outgoingMessageQueue.firstElement().messageContent;
            Socket sender = outgoingMessageQueue.firstElement().sender;
            System.out.println("Sending message : "+messageToSend+" to all clients");
            outgoingMessageQueue.remove(0);
            for(int i=0; i<listOfClients.size(); i++)
            {
        if(!listOfClients.get(i).isClosed())
        {
            //if(listOfClients.get(i)!= sender) // TODO uncomment this line on release
            {
        DataOutputStream out = new DataOutputStream(listOfClients.get(i).getOutputStream());
        out.writeUTF(messageToSend);
            }
        }
        else
        {
            System.out.println("Message not sent to : "+sender.getRemoteSocketAddress()+
            " connected was closed");
        }
            }
        }
            }
        } 
        catch (IOException ex) 
        {
            Logger.getLogger(GlobalSenderThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
class ClientRecieverThread extends Thread
{
    Socket handlingSocket;
    Vector<Message> outgoingMessageQueue;
    Vector<Socket> listOfClients;
    Vector<Message> listOfAllMessages ;
    ClientRecieverThread(Socket handlingSocketVal, Vector<Socket> listOfClientsVal , Vector<Message> outgoingMessageQueueVal, Vector<Message> listOfAllMessagesVal) throws IOException
    {
        handlingSocket=handlingSocketVal;
        outgoingMessageQueue = outgoingMessageQueueVal;
        listOfAllMessages = listOfAllMessagesVal;
        listOfClients = listOfClientsVal;
        OutputStream outToServer = handlingSocket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServer);
        out.writeUTF("Hi! You are hereby connected to the chat server " 
            + handlingSocket.getLocalSocketAddress());
    }
    @Override
    public void run()
    {
        try 
        {
            InputStream inFromServer = handlingSocket.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            while(true)
            {
                String clientReply= in.readUTF();
                System.out.println("Client reply " + clientReply);
                Message recievedMessage = new Message(handlingSocket, clientReply);
                outgoingMessageQueue.add(recievedMessage);
                listOfAllMessages.add(recievedMessage);
                if(clientReply.toLowerCase().contains("disconnect"))
                {
                    listOfClients.remove(handlingSocket);
                    System.out.println("Disconnected from : "+handlingSocket.getRemoteSocketAddress());
                    handlingSocket.close();
                    break;
                }
            }
        } 
        catch (IOException ex) 
        {
            //Logger.getLogger(ClientRecieverThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
class Message
{
    public String messageContent;
    Socket sender;
    Message(Socket senderVal, String messageContentVal)
    {
        sender = senderVal;
        messageContent = messageContentVal;
    }
}

/*
 * a simple static http server
*/
class SimpleHttpServer 
{
    HttpServer server ;
    Vector<Message> listOfAllMessages;
    SimpleHttpServer(Vector<Message> listOfAllMessagesVal) throws IOException 
    {
        listOfAllMessages = listOfAllMessagesVal;
        server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/messages", new MyHandler(listOfAllMessages));
        server.setExecutor(null); // creates a default executor
    }
    public void start()
    {
        server.start();
    }
    static class MyHandler implements HttpHandler 
    {
        Vector<Message> listOfAllMessages;
        MyHandler(Vector<Message> listOfAllMessagesVal)
        {
            listOfAllMessages = listOfAllMessagesVal;
        }
        @Override
        public void handle(HttpExchange t) throws IOException 
        {
            String responseStr = ("<html>\n" +
            "    <style>\n" +
            "    div .container\n" +
            "    {\n" +
            "padding-left: 10%;\n" +
            "padding-right: 10%\n" +
            "    }\n" +
            "    </style>\n" +
            "    <div class=\"container\">\n" +
            "<h1>\n" +
            "    Last 10 Messages\n" +
            "</h1>\n" +
            "</br>\n" +
            "</br>\n" +
            "</br>\n") ;
            for(int i=0; i<10 && i<listOfAllMessages.size(); i++)
            {
                responseStr+="<p>"+listOfAllMessages.get(i).sender.getRemoteSocketAddress()+" : "
                        +listOfAllMessages.get(i).messageContent+"</p>\n";
            }
            responseStr+=("</div>\n" +"</html>") ;
            t.sendResponseHeaders(200, responseStr.getBytes().length);
            OutputStream os = t.getResponseBody();
            os.write(responseStr.getBytes());
            os.close();
      }
    }
}