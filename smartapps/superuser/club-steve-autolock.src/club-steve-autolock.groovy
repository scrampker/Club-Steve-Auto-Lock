/**
 *  Auto-locking door app for the house
 *
 *  Author: stevenascott@gmail.com
 *  Date: 2014-03-28
 */


// Automatically generated. Make future change here.
definition(
    name: "Club Steve AutoLock",
    namespace: "",
    author: "Steven Scott",
    description: "Automatically lock the doors, even if we're home",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png")

preferences
{
	section ("Auto-Lock...")
    	{
		input "contact0", "capability.contactSensor", title: "Which door?"
        	input "lock0","capability.lock", title: "Which lock?"
        	input "autolock_delay", "number", title: "Delay for auto-Lock after door is closed? (Seconds)"
        	input "relock_delay", "number", title: "Delay for re-lock w/o opening door? (Seconds)"
        	input "leftopen_notify", "number", title: "Notify if door open for X seconds."
    	} 
}

def installed()
{
	initialize()
}

def updated()
{
	log.debug "Updating"
	unsubscribe()
	unschedule()
	initialize()
}

def initialize()
{
	log.debug "Initializing"
    
    	subscribe(lock0, "lock", door_handler, [filterEvents: false])
    	subscribe(lock0, "unlock", door_handler, [filterEvents: false])  
    	subscribe(contact0, "contact.open", door_handler)
	subscribe(contact0, "contact.closed", door_handler)
}

def door_handler(evt)
{
	if(evt.value == "closed")
    	{
		unschedule( lock_door )
        	unschedule( notify_door_left_open )
        	log.debug "$contact0 closed, locking after $autolock_delay seconds."
		runIn(autolock_delay, "lock_door")
	}
	if(evt.value == "open")
	{
		unschedule( lock_door )
        	unschedule( notify_door_left_open )
        	unschedule( check_door_actually_locked )
        	log.debug "$contact0 has been opened."
	 	runIn(leftopen_notify, "notify_door_left_open")
	}
    
	if(evt.value == "unlocked")
	{
    		unschedule( lock_door )
    		log.debug "$lock0 was unlocked"
        	runIn(relock_delay, "lock_door")
	}
	if(evt.value == "locked")
	{
    		unschedule( lock_door )
    		log.debug "$lock0 was locked"
	}
}

def lock_door() // auto-lock specific
{
	if (contact0.latestValue("contact") == "closed")
	{
		lock0.lock()
    		log.debug "Sending lock command to $lock0."
        	pause(10000)
        	check_door_actually_locked()     // wait 10 seconds and check thet status of the lock
	}
	else
	{
    		unschedule( lock_door )
    		log.debug "$contact0 is still open, trying to lock $lock0 again in 30 seconds"
        	runIn(30, "lock_door")
	}
}

def check_door_actually_locked() // if locked, reset lock-attempt counter. If unlocked, try once, then notify the user
{
	if (lock0.latestValue("lock") == "locked")
    	{
    		state.lockattempts = 0
    		log.debug "$lock0 is actually locked."
        	sendPush "$lock0 is actually locked."
            unschedule( lock_door )
    	}
    	else
    	{
		state.lockattempts = state.lockattempts + 1
        	if ( state.lockattempts < 3 )
        	{
                        unschedule( lock_door )
        		log.debug "$lock0 attempt #$state.lockattempts."
        		sendPush "$lock0 attempt #$state.lockattempts."
        		runIn(30, "lock_door")
        	}
        	else
        	{
        		log.debug "Locking attempt FAILED! Check out $lock0 immediately!"
            		sendPush "Locking attempt FAILED! Check out $lock0 immediately!"
            		unschedule( lock_door )
        	}
	}
}

def notify_door_left_open()
{
	log.debug"$contact0 has been left open for $leftopen_notify seconds."
	sendPush "$contact0 has been left open for $leftopen_notify seconds."
}

def send_message(msg) 
{
	sendPush msg
}