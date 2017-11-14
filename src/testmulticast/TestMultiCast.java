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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ferhan
 */
public class TestMultiCast {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // TODO code application logic here
        ClientsListener c= new ClientsListener();
        c.start();
    }
    
}
class ClientsListener extends Thread
{
    protected byte[] buf = new byte[256];
    MulticastSocket listeningSocket;
    public ClientsListener() throws IOException 
    {
        listeningSocket = new MulticastSocket(4446);
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
                System.out.println("Just connected to " + client.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(client);
                handler.start();
            }
        }
        catch (IOException ex) 
        {
            Logger.getLogger(ClientsListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
class ClientHandler extends Thread
{
    Socket handlingSocket;
    ClientHandler(Socket handlingSocketVal)
    {
        handlingSocket=handlingSocketVal;
    }
    @Override
    public void run()
    {
        try 
        {
            OutputStream outToServer = handlingSocket.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            InputStream inFromServer = handlingSocket.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            out.writeUTF("Hi! You are hereby connected to the chat server " 
                    + handlingSocket.getLocalSocketAddress());
            Scanner inputScanner = new Scanner(System.in);
            while(true)
            {
                String serverReply= in.readUTF();
                System.out.println("Client reply " + serverReply);
                if(serverReply.toLowerCase().contains("disconnect"))
                {
                    out.writeUTF("Bye");
                    handlingSocket.close();
                    break;
                }
                System.out.print("Please enter a message for client : ");
                String clientResponse= inputScanner.nextLine();
                out.writeUTF(clientResponse);
            }
        } 
        catch (IOException ex) 
        {
            //Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
