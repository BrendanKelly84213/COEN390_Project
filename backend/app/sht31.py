# sht31.py
import time
from smbus2 import SMBus, i2c_msg
import os
import random

FAKE = os.getenv("SENSOR_MODE") == "fake"

sht31_addr = 0x44

# disable clock stretching for now (this has been a headache in the past for me)
# HIGH repeatability, high duration
CMD_MEASURE = (0x24, 0x00)

def sht31_read() -> tuple[float, float]:
    if FAKE:
        return round(random.uniform(20, 25), 2), round(random.uniform(40, 60), 2)
    with SMBus(1) as bus:
        # write the command to read (no clock stretching, high repeatability)
        msg = i2c_msg.write(sht31_addr, CMD_MEASURE) 
        bus.i2c_rdwr(msg);
        time.sleep(0.02) # wait for measurement to finish
        # read 6 bytes from the device (2 bytes temperature, 2 bytes humidity, 2 bytes CRC)
        # note the datasheet mentions something about specific something or other for ignoring the CRC, I think we'll just grab it and ignore it for now until we can be certain about the polynomial 
        rd = i2c_msg.read(sht31_addr, 6) 
        bus.i2c_rdwr(rd)
        data = list(rd)

        raw_t = (data[0] << 8) | data[1]
        raw_h = (data[3] << 8) | data[4]
        
        rel_h = 100 * (raw_h >> 15)  # relative humidity percentage
        temp_c = -45 + 175 * (raw_t >> 15) # temperature in celcius
        temp_f = -49 + 315 * (raw_t >> 15) # temperature in fertnhanht

        return rel_h, temp_c
