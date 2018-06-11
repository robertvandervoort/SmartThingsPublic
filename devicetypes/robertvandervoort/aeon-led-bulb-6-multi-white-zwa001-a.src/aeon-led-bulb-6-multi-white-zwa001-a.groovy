/*
 * V 1.0 of Aeon Labs LED Bulb 6: Multi-White ZWA001-A / ZWA001-C
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * some code used from various SmartThings device type and metering code from ElasticDev
 *
*/

metadata {
	definition (name: "Aeon Labs LED Bulb 6 Multi-White - RV v1.0", namespace: "robertvandervoort", author: "Robert Vandervoort") {
		capability "Actuator"
		capability "Switch"
		capability "Switch Level"
        capability "Configuration"
		capability "Refresh"
		capability "Sensor"

		command "reset"

		/* Capability notes
		0x26 COMMAND_CLASS_SWITCH_MULTILEVEL 2
        0x27 COMMAND_CLASS_SWITCH_ALL 1
        0x59 COMMAND_CLASS_ASSOCIATION_GRP_INFO  1
		0x5A COMMAND_CLASS_DEVICE_RESET_LOCALLY 1
		0x5E COMMAND_CLASS_ZWAVE_PLUS_INFO 2
		0x60 Multi Channel Command Class (V3)
		0x70 COMMAND_CLASS_CONFIGURATION 1
        0x72 COMMAND_CLASS_MANUFACTURER_SPECIFIC 2
        0x73 COMMAND_CLASS_POWERLEVEL 1
		0x7A COMMAND_CLASS_FIRMWARE_UPDATE_MD 2
		0x81 COMMAND_CLASS_CLOCK 1
        0x82 COMMAND_CLASS_HAIL 1
		0x8E COMMAND_CLASS_MULTI_INSTANCE_ASSOCIATION 2
        0x85 COMMAND_CLASS_ASSOCIATION 2
        0x86 COMMAND_CLASS_VERSION 2
		0xEF COMMAND_CLASS_MARK 1
        */
		
		/* 
		zw:Ls type:1101 mfr:0371 prod:0103 model:0001 ver:1.13 zwv:4.61 lib:03 cc:5E,55,9F,98,6C sec:86,72,73,5A,26,33,2B,2C,70,85,59,7A role:05 ff:8600 ui:8600
		*/
		
        fingerprint mfr: "0371", prod: "0103", model: "0001"
		}
	// simulator metadata
	simulator {
}
	// tile definitions
	tiles (scale: 2) {
    	multiAttributeTile(name:"main", type:"lighting", width:6, height:4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
            	attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            }
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}
		standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "main"
		details(["main","refresh","configure"])
	}
	
    preferences { 
		input "ledBehavior", "integer", 
			title: "LED Behavior",
            description: "0=energy tracking, 1=momentary status, 2=night light",
			defaultValue: 0, 
			displayDuringSetup: true,
            range: "0..2"
		input "monitorInterval", "integer", 
			title: "Monitoring Interval", 
			description: "The time interval in seconds for sending device reports", 
			defaultValue: 60, 
            range: "1..4294967295‬",
			required: false, 
			displayDuringSetup: true
		input "ledBrightness", "integer",
        	title: "LED Brightness",
            description: "Set the % brightness of indicator LEDs",
            defaultValue: 50,
            range: "0..100",
            required: false,
            displayduringSetup: true
		input "debugOutput", "boolean", 
			title: "Enable debug logging?",
			defaultValue: false,
			displayDuringSetup: true
	}
}

def updated()
{
	state.debug = ("true" == debugOutput)
	if (state.sec && !isConfigured()) {
		// in case we miss the SCSR
		response(configure())
	}
}

def parse(String description)
{
	def result = null
	if (description.startsWith("Err 106")) {
		state.sec = 0
		result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
			descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.")
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x25: 1, 0x26: 1, 0x27: 1, 0x32: 3, 0x33: 3, 0x59: 1, 0x70: 1, 0x72: 2, 0x73: 1, 0x82: 1, 0x85: 2, 0x86: 2])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	// if (state.debug) log.debug "Parsed '${description}' to ${result.inspect()}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x26: 1, 0x27: 1, 0x32: 3, 0x33: 3, 0x59: 1, 0x70: 1, 0x72: 2, 0x73: 1, 0x82: 1, 0x85: 2, 0x86: 2])
	state.sec = 1
	if (state.debug) log.debug "encapsulated: ${encapsulatedCommand}"
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		createEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
	response(configure())
}

def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd) {
	if (state.debug) log.debug "===Power level test node report received=== ${device.displayName}: statusOfOperation: ${cmd.statusOfOperation} testFrameCount: ${cmd.testFrameCount} testNodeid: ${cmd.testNodeid}"
	def request = [
        physicalgraph.zwave.commands.powerlevelv1.PowerlevelGet()
    ]
    response(commands(request))
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    if (state.debug) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (state.debug) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	def result;
    result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical", displayed: true, isStateChange: true);
    return result;
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	def result;
	result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical", displayed: true, isStateChange: true);
    return result;
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	def result;
    result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital", displayed: true, isStateChange: true);
    return result;
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
	def result;
    result = createEvent(name: "level", value: cmd.value, type: "digital", displayed: true, isStateChange: true);
    return result;
}
	
def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinarySet cmd) {
	def result;
    result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital", displayed: true, isStateChange: true);
    return result;
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	if (state.debug) log.debug "Unhandled: $cmd sent to ${device.displayName}"
    createEvent(descriptionText: cmd.toString(), isStateChange: false)
}

def on() {
	if (state.debug) log.debug "turning on ${device.displayName}"
	def request = [
		zwave.basicV1.basicSet(value: 0xFF),
	    zwave.basicV1.basicGet(),
		zwave.switchBinaryV1.switchBinaryGet(),
		zwave.switchMultilevelV1.switchMultilevelGet()
	]
    commands(request)
}

def off() {
	if (state.debug) log.debug "turning off ${device.displayName}"
	def request = [
		zwave.basicV1.basicSet(value: 0x00),
        zwave.basicV1.basicGet(),
		zwave.switchBinaryV1.switchBinaryGet(),
		zwave.switchMultilevelV1.switchMultilevelGet()
	]
    commands(request)
}

def setLevel(value) {
	if (state.debug) log.debug "setting level to ${value} on ${device.displayName}"
	def valueaux = value as Integer
	def level = Math.min(valueaux, 99)
	def request = [
		zwave.basicV1.basicSet(value: level),
		zwave.switchMultilevelV1.switchMultilevelGet()
		]
	commands(request)	
}

def setLevel(value, duration) {
	def valueaux = value as Integer
	def level = Math.min(valueaux, 99)
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	def request = [
		zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration)
		]
	commands(request)	
}

def refresh() {
	if (state.debug) log.debug "refresh request sent to ${device.displayName}"
	def request = [
        zwave.basicV1.basicGet(),
		zwave.switchMultilevelV1.switchMultilevelGet()
	]
    commands(request)
}

def configure() {
	if (state.debug) {
    	if (state.sec) {
        	log.debug "secure configuration being sent to ${device.displayName}"
            }
        else
        	if (state.debug) log.debug "configuration being sent to ${device.displayName}"
		}            
	def ledBright = 50
    	if (ledBrightness) {
        	ledBright=ledBrightness.toInteger()
		}
    if (state.debug) log.debug "Sending configure commands - monitorInterval '${monitorInt}', ledBehavior '${ledBehave}'"
	
	/*
	0x50 Enable to send notifications to associated devices (Group 1) when the state of LED Bulb is changed.
		0 = Nothing.
		1= Basic CC report.
	0x51 Adjusting the color temperature in warm white color component.
		available value: 0x0A8C-1387 Warm White(0x0A8C(2700k) ~ 0x1387 (4999k))
		0x0A8C
	0x52 Adjusting the color temperature in cold white color component.
		available value: 0x1388-0x1964 Cold White (0x1388 (5000k) ~ 0x1964 (6500k))
		0x1964
	*/
	
	
	def request = [
 		// Enable to send notifications to associated devices (Group 1) when the state of LED Bulb is changed.
        zwave.configurationV1.configurationSet(parameterNumber: 0x50, size: 1, configurationValue: [1]),
        
		// set warm LED TEMP 0x0A8C-1387 Warm White(0x0A8C(2700k) ~ 0x1387 (4999k))
		zwave.configurationV1.configurationSet(parameterNumber: 0x51, size: 2, configurationValue: [0x0A8C]),
		
		// Set cool LED TEMP 0x1388-0x1964 Cold White (0x1388 (5000k) ~ 0x1964 (6500k))
        zwave.configurationV1.configurationSet(parameterNumber: 0x52, size: 2, configurationValue: [0x1964]),

		// Can use the zwaveHubNodeId variable to add the hub to the device's associations:
		// zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId)
    ]
	commands(request)
}

private setConfigured() {
	updateDataValue("configured", "true")
}

private isConfigured() {
	getDataValue("configured") == "true"
}

private command(physicalgraph.zwave.Command cmd) {
	if (state.sec) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=500) {
	delayBetween(commands.collect{ command(it) }, delay)
}