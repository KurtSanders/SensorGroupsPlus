/**
 *
 * Sensor Groups+_Switch
 *
 * Copyright 2022 Ryan Elliott
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * v1.0		RLE		Creation
 */
 
definition(
    name: "Sensor Groups+_Switch",
    namespace: "rle.sg+",
    author: "Ryan Elliott",
    description: "Creates a virtual device to track a group of switch devices.",
    category: "Convenience",
	parent: "rle.sg+:Sensor Groups+",
	iconUrl: "",
    iconX2Url: "")

preferences {
    page(name: "prefSwitchGroup")
	page(name: "prefSettings")
}

def prefSwitchGroup() {
	return dynamicPage(name: "prefSwitchGroup", title: "Create a Switch Group", nextPage: "prefSettings", uninstall:true, install: false) {
		section {
            label title: "<b>***Enter a name for this child app.***</b>"+
            "<br>This will create a virtual switch sensor which reports the on/off status based on the switches you select.", required:true
		}
	}
}

def prefSettings() {
    return dynamicPage(name: "prefSettings", title: "", install: true, uninstall: true) {
		section {
			paragraph "<b>Please choose which sensors to include in this group.</b>"+
            "<br>The virtual device will report status based on the configured threshold."

			input "switchSensors", "capability.switch" , title: "Switch sensors to monitor", multiple:true, required:true
        }
		section {
            paragraph "Set how many sensors are required to change the status of the virtual device."
            
            input "activeThreshold", "number", title: "How many sensors must be on before the group is on? Leave set to one if any switch on should make the group on.", required:false, defaultValue: 1
            
            input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false
        }
	}
}

def installed() {
	initialize()
}

def uninstalled() {
	logDebug "uninstalling app"
	for (device in getChildDevices())
	{
		deleteChildDevice(device.deviceNetworkId)
	}
}

def updated() {	
    logDebug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
	initialize()
}

def initialize() {
	subscribe(switchSensors, "switch", switchHandler)
	createOrUpdateChildDevice()
    switchHandler()
    def device = getChildDevice(state.switchDevice)
    device.sendEvent(name: "TotalCount", value: switchSensors.size())
	device.sendEvent(name: "OnThreshold", value: activeThreshold) 
    runIn(1800,logsOff)
}

def switchHandler(evt) {
    log.info "Switch changed, checking status count..."
    getCurrentCount()
    def device = getChildDevice(state.switchDevice)
	if (state.totalOn >= activeThreshold)
	{
		log.info "On threshold met; setting virtual device as on"
		logDebug "Current threshold value is ${activeThreshold}"
		device.sendEvent(name: "switch", value: "on", descriptionText: "The on devices are ${state.onList}")
	} else {
		log.info "On threshold not met; setting virtual device as off"
		logDebug "Current threshold value is ${activeThreshold}"
		device.sendEvent(name: "switch", value: "off")
	}
}

def createOrUpdateChildDevice() {
	def childDevice = getChildDevice("switchgroup:" + app.getId())
    if (!childDevice || state.switchDevice == null) {
        logDebug "Creating child device"
		state.switchDevice = "switchgroup:" + app.getId()
		addChildDevice("rle.sg+", "Sensor Groups+_OmniSensor", "switchgroup:" + app.getId(), 1234, [name: app.label + "_device", isComponent: false])
    }
	else if (childDevice && childDevice.name != (app.label + "_device"))
		childDevice.name = app.label + "_device"
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def getCurrentCount() {
	def device = getChildDevice(state.switchDevice)
	def totalOn = 0
    def totalOff = 0
	def onList = []
	switchSensors.each { it ->
		if (it.currentValue("switch") == "on")
		{
			totalOn++
			if (it.label) {
            onList.add(it.label)
            }
            else if (!it.label) {
                onList.add(it.name)
            }
		}
		else if (it.currentValue("switch") == "off")
		{
			totalOff++
		}
    }
    state.totalOn = totalOn
	if (onList.size() == 0) {
        onList.add("None")
    }
	state.onList = onList.sort()
    logDebug "There are ${totalOff} sensors off"
    logDebug "There are ${totalOn} sensors on"
    device.sendEvent(name: "TotalOff", value: totalOff)
    device.sendEvent(name: "TotalOn", value: totalOn)
	device.sendEvent(name: "OnList", value: state.onList)
}