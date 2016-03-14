/**
 *  Auto-locking door app for the house
 *
 *  Author: stevenascott@gmail.com
 *  Date: 2016 03 13
 *  Version: Beta 0.3
 */


// Automatically generated. Make future change here.
definition(
    name: "Club Steve AutoLock",
    namespace: "",
    author: "Steven Scott",
    description: "Intelligent door-locking SmartApp",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png")

preferences
{
	section ("Auto-Lock...")
    {
		input "pref_contact_sensor", "capability.contactSensor", title: "Which contact sensor?", required: true
        input "pref_door_lock","capability.lock", title: "Which lock?", required: true
		input "pref_use_accelerometer", "enum", title: "Would you like to use the accelerometer features?", metadata: [values: ["Yes","No"]], required: true, defaultValue: "Yes"
        input "pref_door_closed_lock_delay", "number", title: "Delay for auto-Lock after door is closed? (Seconds)", required: true, defaultValue: 0
        input "pref_door_unlocked_relock_delay", "number", title: "Delay for re-lock w/o opening door? (Seconds)", required: true, defaultValue: 300
        input "pref_locking_attempts", number, title: "How many times should we send the locking command?", required: true, defaultValue: 1
        input "pref_locking_attempt_delay", number, title: "^^Delay between attempts? (Seconds: Keep it under 5)", required: true, defaultValue: 1
		input "pref_failed_locking_reattempts", number, title: "Upon locking failure, how many times should we try total?", required: true, defaultValue: 1
		input "pref_failed_locking_reattempts_delay", number, title: "^^Delay between attempts? (Seconds)", required: true, defaultValue: 15
        input "pref_leftopen_delay", "number", title: "Notify if door open for X seconds.", required: true, defaultValue: 300
        input "pref_push_enabled", "enum", title: "Enable NORMAL push notifications?", metadata: [values: ["Yes","No"]], required: true, defaultValue: "Yes"
        input "pref_debug_notify", "enum", title: "Enable DEBUG push notifications?", metadata: [values: ["Yes","No"]], required: true, defaultValue: "No"
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
    
    subscribe(pref_door_lock, "lock", lock_handler)
    subscribe(pref_door_lock, "unlock", lock_handler)  
    subscribe(pref_contact_sensor, "contact.open", door_handler)
	subscribe(pref_contact_sensor, "contact.closed", door_handler)
	subscribe(pref_contact_sensor, "acceleration.active", acceleration_handler)
	subscribe(pref_contact_sensor, "acceleration.inactive", acceleration_handler)
	
	state.locking_scheduled = "false"
	state.failed_lock_attempts = 0
	state.current_lock_delay = "test"
}

def debug_handler(msg)
{
	log.debug msg
	if(pref_debug_notify == "Yes")
	{
		sendPush msg
	}
}

def push_handler(msg)
{
	if(pref_push_enabled == "Yes")
	{
		sendPush msg
	}
}

def acceleration_handler()
{
	if(pref_use_accelerometer == "true" && state.locking_scheduled == "true") // User preference decides if we do anything with the acceleration aspect of the contact sensor (assuming it exists)
	{
		if(evt.value == "active") // Unschedule locking, since the door is active.
		{
			debug_handler("$pref_contact_sensor detected acceleration, unscheduling lock/notify.")
			unschedule( lock_door )
			unschedule( notify_door_left_open )
		}
		if(evt.value == "inactive")
		{
			debug_handler("$pref_contact_sensor is idle, re-scheduling the locking activity.")
			schedule_lock(state.current_lock_delay) // Since we unscheduled the locking activity, we need to reschedule it
		}
	}
}

def schedule_lock(locking_delay)
{
	state.current_lock_delay = "$locking_delay" // Update the global current delay (switches between lock/relock preference)
	state.failed_lock_attempts = 0
	state.locking_scheduled = "true"
	
	debug_handler("Locking $pref_door_lock in $state.current_lock_delay seconds.")
	debug_handler("Locking $pref_door_lock in $locking_delay seconds.")
	
	if(state.current_lock_delay == 0) // Bypass the runIn mechanism, which sucks balls
	{
		lock_door()
	}
	else
	{
		runIn(state.current_lock_delay, "lock_door")
	}
}

def lock_handler(evt)
{
	if(evt.value == "unlocked")
	{
    	unschedule( lock_door )
        unschedule( check_door_actually_locked )
        state.failed_lock_attempts = 0 // reset the counter due to manual unlock
    	debug_handler("$pref_door_lock was unlocked.")
        schedule_lock(pref_door_unlocked_relock_delay)
	}
	
	if(evt.value == "locked") // since the lock is reporting LOCKED, action stops.
	{
    	unschedule( lock_door )
    	debug_handler("$pref_door_lock reports: LOCKED")
		state.locking_scheduled = "false"
	}
}

def door_handler(evt)
{
	if(evt.value == "closed")
    {
		unschedule( lock_door )
    	unschedule( notify_door_left_open )
       	state.failed_lock_attempts = 0
		state.total_lock_attempts = 0 // resetting attempts so it tries the default amount
       
       	schedule_lock(pref_door_closed_lock_delay) // Schedule the locking activity based on the user preference when the door is closed.
	}
	
	if(evt.value == "open") // When the door is opened, all activity is canceled, other than notifying when left open
	{
		unschedule( lock_door )
        unschedule( notify_door_left_open )
        unschedule( check_door_actually_locked )
        state.failed_lock_attempts = 0 // reset the counter due to door being opened
		state.total_lock_attempts = 0 // resetting attempts so it tries the default amount
        debug_handler("$pref_contact_sensor has been opened.")
	 	runIn(pref_leftopen_delay, "notify_door_left_open")
		state.locking_scheduled = "false"
	}
}

def lock_door() // auto-lock specific
{
	debug_handler("Entering lock_door function.")
	if (state.locking_scheduled == "true") // if the door has been opened, unlocked, etc, this flag will prevent it from tryagin again when we don't want to
	{
		debug_handler("Locking was previously scheduled.")
		okay_to_lock = "true"
		if (pref_contact_sensor.latestValue("contact") == "open") // if the door is open, flag unsafe for locking
		{
			debug_handler("$pref_contact_sensor is open, attempting again in 30 seconds.")
			okay_to_lock = "false"
		}
		
		if (pref_use_accelerometer == "true" && pref_contact_sensor.latestValue("acceleration") == "active") // if the door is in motion, flag unsafe for locking1
		{
			debug_handler("$pref_contact_sensor is active, attempting again in 30 seconds.")
			okay_to_lock = "false"	
		}
		
		if (okay_to_lock == "true")
		{
			if (state.total_lock_attempts < $pref_locking_attempts)
			{
				debug_handler("Sending lock command to $pref_door_lock.")
				pref_door_lock.lock() // The actual lock command.
				state.total_lock_attempts = $state.total_lock_attempts + 1
				if (pref_locking_attempts > 1) // if we're only making one attempt, there's no poing in having any delay here
				{
					runIn(pref_locking_attempt_delay, "lock_door") // adding in the delay between multiple attempts
				}
			}
				
			debug_handler("Checking in 30 seconds to see if the lock actually completed the locking activity.")
			runIn(30, "check_door_actually_locked") // then check the status of the lock again
		}
		else
		{
			unschedule( lock_door ) // it's not safe to lock, so try again in 30 seconds
			runIn(30, "lock_door")
		}
	}
	else
	{
		debug_handler("Lock attempt was scheduled, but the flag was removed")
	}
}

def check_door_actually_locked() // if locked, reset lock-attempt counter. If unlocked, try once, then notify the user
{
	if (pref_door_lock.latestValue("lock") == "locked")
    {
    	state.failed_lock_attempts = 0
		state.total_lock_attempts = 0 // resetting attempts so it tries the default amount
    	debug_handler("Double-checking $pref_door_lock: LOCKED")
       	unschedule( lock_door )
       	unschedule( check_door_actually_locked )
       	if(state.lockstatus == "failed")
       	{
       		debug_handler("$pref_door_lock has recovered and is now locked!")
       		push_handler("$pref_door_lock has recovered and is now locked!")
      		state.lockstatus = "okay"
       	}
		state.locking_scheduled = "false"
    }
    else // if the door doesn't show locked, try again, if the user asked to
    {
		state.failed_lock_attempts = $state.failed_lock_attempts + 1
		if ( $state.failed_lock_attempts < $pref_failed_locking_reattempts )
        {
			unschedule( lock_door )
			debug_handler("$pref_door_lock lock attempt #$state.failed_lock_attempts of $pref_failed_locking_reattempts failed.")
			state.total_lock_attempts = 0 // resetting attempts so it tries the default amount
            runIn($pref_failed_locking_reattempts_delay, "lock_door")
        }
        else
        {
			debug_handler("ALL Locking attempts FAILED! Check out $pref_door_lock immediately!")
			push_handler("ALL Locking attempts FAILED! Check out $pref_door_lock immediately!")
			state.lockstatus = "failed"
			unschedule( lock_door )
			unschedule( check_door_actually_locked )
			state.total_lock_attempts = 0 // resetting attempts so it tries the default amount
        }
	}
}

def notify_door_left_open()
{
	debug_handler("$pref_contact_sensor has been left open for $pref_leftopen_delay seconds.")
	push_handler("$pref_contact_sensor has been left open for $pref_leftopen_delay seconds.")
}
