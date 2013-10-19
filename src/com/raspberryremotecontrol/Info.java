/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.raspberryremotecontrol;

/**
 *
 * @author Lorenzo
 */
public class Info {

    public int Icon;
    public String Name;
    public String Description;
    public int ProgressBarProgress;

    public Info() {
        super();
    }

    public Info(int Icon, String Name, String Description, int ProgressBarProgress) {
        super();
        this.Icon = Icon;
        this.Name = Name;
        this.Description = Description;
        this.ProgressBarProgress = ProgressBarProgress;
    }
}
