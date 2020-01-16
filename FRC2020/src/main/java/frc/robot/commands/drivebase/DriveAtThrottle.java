/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frc.robot.commands.drivebase;

import edu.wpi.first.wpilibj.command.Command;

import frc.robot.Robot;

/**
 * Drive the given distance straight at a given throttle value.
 */
public class DriveAtThrottle extends Command {
	double throttleFwd;
	
	public DriveAtThrottle(double throttleFwd) {
		requires(Robot.drivebase);
		System.out.println("We are in DriveAtThrottle");
		
		// Not sure why this is reversed
		this.throttleFwd = -throttleFwd;
		
	}

	// Called every time the command starts
	@Override
	public void initialize() {
		System.out.println("Starting DriveAtThrottle (" + throttleFwd + ")");
		
		// Command the movement
		Robot.drivebase.arcade(throttleFwd, 0);
		
	}

	// Make this return true when this Command no longer needs to run execute()
	@Override
	protected boolean isFinished() {
		return false; // continue until stoppes
	}

	// Called once after isFinished returns true
	@Override
	protected void end() {
		System.out.println("Ending DriveAtThrottle (" + throttleFwd + ")");
		Robot.drivebase.stop();
	}
}
