/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.raspberryremotecontrol;

/**
 *
 * @author Lorenzo
 */
public class Profile {

    String Name;
    String IpAddress;
    String Username;
    String Password;

    public Profile(String Name, String IpAddress, String Username, String Password) {
        this.Name = Name;
        this.IpAddress = IpAddress;
        this.Username = Username;
        this.Password = Password;
    }
}
