# MobiQoE

optimizing Android system based on user behavior, i.e., bringing human behavior into system optimization. 
The initial contribution is identifying multimedia traffic without looking into the traffic packets and 
then modify the protocol header with the desired DSCP code to enfore the QoS requirments of multimedia applications. 
It relies of At&T ARO VPN client to do so. However, ARO is not suitable though. We may need to modify the system kernel so that it can update the protocol header by itself. 

The project is locally maintained and you may see infrequent updates. 
