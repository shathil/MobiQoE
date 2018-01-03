//
// Created by Hoque, Mohammad on 22/07/2017.
//

/*
 * This code is taked from the normal C-code of the
 *
 * bcastclient.c
 *
 *  Created on: Dec 1, 2016
 *      Author: mohoque
 */


/* udp-broadcast-client.c
 * udp datagram client
 * Get datagram stock market quotes from UDP broadcast:
 * see below the step by step explanation
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <linux/if.h>
#include <ctype.h>
#include <netdb.h>

#define APPNAME "MobiQoE"


extern "C"
void
Java_ektara_com_wisensetunnel_WSenseActivity_setserviceParams(
        JNIEnv *env,
        jobject, jstring address, jint port, jint slots) {
    //std::string hello = "Hello from C++";
    //return env->NewStringUTF(hello.c_str());
}

extern "C"
jstring
Java_ektara_com_wisensetunnel_WSenseActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}



extern int mkaddr(
        void *addr,
        int *addrlen,
        char *str_addr,
        char *protocol);

/*
 * This function reports the error and
 * exits back to the shell:
 */

void ad_log(char *message){



    __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, "%s",message);
}

static void
displayError(const char *on_what) {
    fputs(strerror(errno),stderr);
    fputs(": ",stderr);
    fputs(on_what,stderr);
    fputc('\n',stderr);
    char message[1024];
    sprintf(message, "%s%s",strerror(errno),on_what);
    ad_log(message);
    exit(1);
}


#define BUFLEN 65000
#define BLCK_LEN 63000

bool service_mutex= true;

//int sfd;
void getiwconfig(int *speed, int *quality, int *signal);
//struct TDMAControlPacket set_control_packet(int pckType, char *id, int duration, int roundtime);


struct TDMAControlPacket
{
    int type;
    char id[18];
    int slot_duration;
    int round_time;
    char join_port[40];
    char data_port[40];
};

struct UdpPacket
{
    int pck_id;
    char id[18];
    char dtbuf[BLCK_LEN + 1];
    long send_time_sec;
    long send_time_usec;
    int speed;
    int quality;
    int signal;
    int end_of_transmission;
};


struct UdpPacket set_tdma_packet(int pck_no, char *databuffer, char *id, int end_tx, struct timeval ts)
{
    int speed = 0;
    int quality = 0;
    int signal = 0;

    struct UdpPacket pck;
    bzero(&pck, sizeof(pck));
    pck.pck_id = pck_no;
    strcpy(pck.id, id);
    strcpy(pck.dtbuf, databuffer);
    pck.send_time_sec = ts.tv_sec;
    pck.send_time_usec = ts.tv_usec;
    pck.end_of_transmission = end_tx;
    pck.speed = speed;
    pck.quality = quality;
    pck.signal = signal;

    return pck;
}

void  getiwconfig(int *speed, int *quality, int *signal)
{

    FILE *fp;
    int status;
    char path[1035];

    /* Open the command for reading. */
    fp = popen("iwconfig wlan0", "r");
    if (fp == NULL) {
        printf("Failed to run command\n" );

        *speed = 0;
        *quality = 0;
        *signal = 0;
        pclose(fp);

        printf("I am returninfg   from hell\n");
        return;
    }
    char s[500];
    char tip[4];
    char *nptr;
    /* Read the output a line at a time - output it. */
    while (fgets(path, sizeof(path)-1, fp) != NULL) {
        memset(s,'\0',500);
        sprintf(s,"%s%s",s, path);

        if (strstr(s,"Bit Rate=")!=NULL){

            nptr=strstr(s,"Bit Rate=");
            memset(tip,'\0',4);
            memcpy(tip,nptr+strlen("Bit Rate="), 3);
            *speed  =  atoi(tip);
        }

        if (strstr(s,"Link Quality=")!=NULL){

            nptr=strstr(s,"Link Quality=");
            memset(tip,'\0',4);
            memcpy(tip,nptr+strlen("Link Quality="), 3);
            *quality = atoi(tip);
        }
        if (strstr(s,"Signal level=")!=NULL){

            nptr=strstr(s,"Signal level=");
            memset(tip,'\0',4);
            memcpy(tip,nptr+strlen("Signal level="),3);
            *signal  =  atoi(tip);
        }


    }

    pclose(fp);
}




int mkaddr(void *addr,
           int *addrlen,
           char *str_addr,
           char *protocol) {

    char *inp_addr = strdup(str_addr);
    char *host_part = strtok(inp_addr, ":" );
    char *port_part = strtok(NULL, "\n" );
    struct sockaddr_in *ap =
            (struct sockaddr_in *) addr;
    struct hostent *hp = NULL;
    struct servent *sp = NULL;
    char *cp;
    long lv;

    /*
     * Set input defaults:
     */
    if ( !host_part ) {
        host_part =  "*" ;
    }
    if ( !port_part ) {
        port_part =  "*" ;
    }
    if ( !protocol ) {
        protocol =  "tcp" ;
    }

    /*
     * Initialize the address structure:
     */
    memset(ap,0,*addrlen);
    ap->sin_family = AF_INET;
    ap->sin_port = 0;
    ap->sin_addr.s_addr = INADDR_ANY;

    /*
     * Fill in the host address:
     */
    if ( strcmp(host_part, "*" ) == 0 ) {
        ; /* Leave as INADDR_ANY */
    }
    else if ( isdigit(*host_part) ) {
        /*
         * Numeric IP address:
         */
        ap->sin_addr.s_addr =
                inet_addr(host_part);
        // if ( ap->sin_addr.s_addr == INADDR_NONE ) {
        if ( !inet_aton(host_part,&ap->sin_addr) ) {
            return -1;
        }
    }
    else {
        /*
         * Assume a hostname:
         */
        hp = gethostbyname(host_part);
        if ( !hp ) {
            return -1;
        }
        if ( hp->h_addrtype != AF_INET ) {
            return -1;
        }
        ap->sin_addr = * (struct in_addr *)
                hp->h_addr_list[0];
    }

    /*
     * Process an optional port #:
     */
    if ( !strcmp(port_part, "*" ) ) {
        /* Leave as wild (zero) */
    }
    else if ( isdigit(*port_part) ) {
        /*
         * Process numeric port #:
         */
        lv = strtol(port_part,&cp,10);
        if ( cp != NULL && *cp ) {
            return -2;
        }
        if ( lv < 0L || lv >= 32768 ) {
            return -2;
        }
        ap->sin_port = htons( (short)lv);
    }
    else {
        /*
         * Lookup the service:
         */
        sp = getservbyname( port_part, protocol);
        if ( !sp ) {
            return -2;
        }
        ap->sin_port = (short) sp->s_port;
    }
    /*
    * Return address length
    */
    *addrlen = sizeof *ap;

    free(inp_addr);
    return 0;
}

static char *interface_mactoa()
{
    static char buff[256];

    struct ifreq s;
    int fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_IP);

    strcpy(s.ifr_name, "wlan0");
    if (0 == ioctl(fd, SIOCGIFHWADDR, &s))
    {
        unsigned char *ptr = (unsigned char*) s.ifr_addr.sa_data;

        sprintf(buff, "%02X:%02X:%02X:%02X:%02X:%02X",
                (ptr[0] & 0xff), (ptr[1] & 0xff), (ptr[2] & 0xff),
                (ptr[3] & 0xff), (ptr[4] & 0xff), (ptr[5] & 0xff));
    }

    return (buff);
}




struct TDMAControlPacket set_control_packet(int pckType, char *id, int duration, int roundtime)
{
    struct TDMAControlPacket pck;
    bzero(&pck, sizeof(pck));
    pck.type = pckType;
    strcpy(pck.id, id);
    pck.slot_duration = duration;
    pck.round_time = roundtime;
    return pck;
}


void  send_packetsto_server(int dt_sock, struct sockaddr_in dt_addr,int slot_time){
    struct timeval start, end;
    char message[1024];
    gettimeofday(&start, NULL);
    int pck_no=0, pack_count=0;
    char sdbuf[BLCK_LEN];
    memset(sdbuf,'\0',BLCK_LEN);
    memset(sdbuf,'\a', BLCK_LEN-1);
    struct UdpPacket data = set_tdma_packet(pck_no, sdbuf, interface_mactoa(), 0,start);
    int len = sizeof dt_addr;


    while(1){
        gettimeofday(&end, NULL);
        if (((end.tv_sec * 1000000 + end.tv_usec) - (start.tv_sec * 1000000 + start.tv_usec))>=slot_time)
            break;

        data.pck_id = pck_no;
        data.send_time_sec = end.tv_sec;
        data.send_time_usec = end.tv_usec;
        if(sendto(dt_sock, (const char *)&data, sizeof(data), 0, (struct sockaddr *)&dt_addr, len) == -1)
            displayError("sendto FILE:");


        pck_no++;
        pack_count++;

        gettimeofday(&end, NULL);
        if (((end.tv_sec * 1000000 + end.tv_usec) - (start.tv_sec * 1000000 + start.tv_usec))>=(slot_time-5000)){


            struct UdpPacket data_total = set_tdma_packet(pack_count, "TOTAL", interface_mactoa(), 9, end);
            if(sendto(dt_sock, (const char *)&data_total, sizeof(data_total), 0, (struct sockaddr *)&dt_addr, len) == -1)
                displayError("sendto FILE:");

            double total_data = pack_count * sizeof(data);
            double data_transferred = total_data/(1024 * 1024);
            int proc_time = (end.tv_sec * 1000000 + end.tv_usec) - (start.tv_sec * 1000000 + start.tv_usec);
            double bandwidth = (data_transferred * 8 * 1000000) / proc_time;

            memset(message,'\0',1024);
            sprintf(message,"%d\t%d\t%d\t%d\t%f\t%f\t%ld.%ld\t%d \n", proc_time, pack_count, pck_no, sizeof(data), data_transferred, bandwidth, data.send_time_sec, data.send_time_usec,data_total.speed);
            ad_log(message);
            break;

        }//end of if

    }// end of while
}


int reset_socket(char* bc_addr, struct sockaddr_in adr){
    int s = socket(AF_INET,SOCK_DGRAM,0);
    int len_inet = sizeof adr;
    int z=0;
    if ( s == -1 )
        displayError("socket()");

    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 2000;

    if(setsockopt(s, SOL_SOCKET, SO_RCVTIMEO,&tv,sizeof(tv)) < 0)
        displayError("Setting socket option error");

    z = mkaddr(&adr,
               &len_inet,
               bc_addr,
               "udp");

    if ( z == -1 )
        displayError("Bad broadcast address");


    z = bind(s,(struct sockaddr *)&adr,len_inet);
    if ( z == -1 )
        displayError("bind(2)");


    return s;
}


bool getnonblocking_socket( int sfd){

    int flags;

    bool set = true;

/* Set socket to non-blocking */


    if ((flags = fcntl(sfd, F_GETFL, 0)) < 0)
    {
        /* Handle error */
        ad_log("Inital nonblocking failed");
        set = false;
    }


    if (fcntl(sfd, F_SETFL, flags | O_NONBLOCK) < 0)
    {
        /* Handle error */
        ad_log("Second attempt nonblocking failed");
        set = false;


    }

    return set;
}


jint Java_ektara_com_wisensetunnel_WSenseActivity_stopService (int argc,char **argv) {

    ///set the number of slots

}


void settcpsocket_properties(int sockfd, bool nonblocking, int dscp){


    int ldscp = dscp;
    if(!getnonblocking_socket(sockfd))
        ad_log("could not convert to nn");
    if (setsockopt(sockfd, IPPROTO_IP, IP_TOS, &ldscp, sizeof(ldscp))<0)
        ad_log("setsockopt DSCP failed\n");

}

//Service discovery

char *atdma_service_discovery(){


}

void send_control_message(JNIEnv *env, int mesage) {
    //jclass cls = //env->FindClass(env, "com/example/utils/CECUtils");

    jclass cls = env->FindClass("ektara/com/datacontrol/BroadcastMessageQueue");
    if(!cls) {


        //LOGE("Could not find the CEC class.");
    } else {
        jmethodID methodid = env->GetStaticMethodID(cls, "gate_control_message", "(I)V");
        if(!methodid) {
            // Code always reaches this point, never finding the method
            //  LOGE("Could not find the callback method.");
        } else {
            //LOGV("Called static void method.");
            (env)->CallStaticVoidMethod(cls, methodid, mesage);
        }
    }
}



extern "C"
jint
Java_ektara_com_services_GateKeeperManager_startService  (JNIEnv *env,
                                                          jobject, jint totslots, jstring address, jstring bcast_port) {

    int slots = (int) totslots;
    int z;
    socklen_t p;
    struct sockaddr_in adr;
    int s=0;
    char dgram[BUFLEN];


    const char *bc_port = env->GetStringUTFChars(bcast_port, JNI_FALSE);
    const char *bc_adr = env->GetStringUTFChars(address, JNI_FALSE);
    char bc_addr[20];
    memset(bc_addr,'\0',20);
    sprintf(bc_addr,"*:%s",bc_port);
    //sprintf(bc_addr,"255.255.255.255:%s",bc_port);

    int slot_count = 0;
    struct timeval start;
    gettimeofday(&start,NULL);

    struct TDMAControlPacket *pck;
    char mac_addr[18];
    memset(&mac_addr, '\0', 18);
    strcpy(mac_addr,bc_adr);

    char message[1024];
    sprintf(message,"Slots: %d  ...and ...Adress %s...Mac %s",slots,bc_addr, mac_addr);
    ad_log(message);


    //return 0;

    /*
     * Scan Broadcast messages for the aTDMA protocol when connected to wifi
     * This service can be offered at the Accesspoint through linux avahi prtocol
     * */
    //if ( argc > 1 )
    /* Broadcast address: */
    // bc_addr = strcat(bc_addr, (char*)bcast_port);

    /*
     * Create a UDP socket to use:
     */
    //strcpy(mac_addr, interface_mactoa());
    //ad_log(mac_addr);
    int join = 0;
    s=reset_socket((char *)bc_addr,adr);


    int m = socket(AF_INET,SOCK_DGRAM,0);


    for (;;) {


        struct timeval end;
        //gettimeofday(&end,NULL);

        /*
       int proc_time = (end.tv_sec * 1000000 + end.tv_usec) - (start.tv_sec * 1000000 + start.tv_usec);
       if ((proc_time>=10000000)&&(slot_count==0)){

           memset(message,'\0',1024);
           sprintf(message,"%d passed and returning", proc_time);
           ad_log(message);
           break;

       }*/
        /*
         * Wait for a broadcast message:
         */
        memset(dgram,'\0',BUFLEN);
        z = recvfrom(s,      /* Socket */
                     dgram,  /* Receiving buffer */
                     BUFLEN,/* Max rcv buf size */
                     0,      /* Flags: no  options */
                     (struct sockaddr *)&adr, /* Addr */
                     &p);    /* Addr len, in & out */

        if(z>0){


            pck = (struct TDMAControlPacket *)dgram;
            struct sockaddr_in jadr;
            int jlen = sizeof jadr;
            int r = mkaddr(&jadr,
                           &jlen,
                           pck->join_port,
                           "udp");

            if ( r < 0 )
                displayError("mkaddr join address error"); /* else err */
            else
                ad_log(pck->join_port);

            if((pck->type==0)&& (join ==0)){

                send_control_message(env, 1);
                memset(message,'\0',104);
                sprintf(message,"Trying to join %s", pck->id);
                ad_log(message);
                struct TDMAControlPacket jpck;


                jpck = set_control_packet(0, mac_addr, 1000,0);
                if(sendto(s, (const char *)&jpck, sizeof(jpck), 0, (struct sockaddr *)&jadr, jlen) == -1)
                    displayError("sending join packet error");
                else{

                    memset(message,'\0',1024);
                    sprintf(message," sent join packet successfully");
                    ad_log(message);

                }

                join = 1;
            }

            if((pck->type==1) && (join ==1)&&strcmp(mac_addr,pck->id)==0){
                //printf("data %s\n",pck->data_port);
                slot_count += 1;
                /*
                struct sockaddr_in dadr;
                int dlen = sizeof dadr;

                r = mkaddr(&dadr,
                               &dlen,
                               pck->data_port,
                               "udp");


                if(r >= 0)
                    send_packetsto_server(m,dadr,pck->slot_duration);
                */
                usleep(pck->slot_duration-5000);
                gettimeofday(&end, NULL);
                struct UdpPacket end_data;
                end_data = set_tdma_packet(0, "END_TX", mac_addr, 1, end);

                if(sendto(s, (const char *)&end_data, sizeof(end_data), 0, (struct sockaddr *)&jadr, jlen) == -1)
                    displayError("sendto");
                else{

                    memset(message,'\0',1024);
                    sprintf(message,"%d sent endof tx packet successfully", slot_count);
                    ad_log(message);

                }
                //usleep(pck->round_time-pck->slot_duration);
                send_control_message(env,0);
                join = 0;

            }

            if(totslots == slot_count)
                break;

        }// end of of Z > 0
    }
    env->ReleaseStringUTFChars(bcast_port, bc_port);
    env->ReleaseStringUTFChars(address, bc_adr);
    close(s);
    close(m);
    return  slot_count;
}

JNIEXPORT jint JNICALL Java_com_qoeapps_nativesocks_NativeJavaSockInterface_initializeTCPSocket(JNIEnv *env, jobject jobj) {
    int sfd = socket(AF_INET, SOCK_DGRAM, 0);

    /*
     * Idea would be generate the socket and protect the socket and then exchange informaiton from heere
     * */
    // . . . Handler other socket preparations

    return (jint)sfd;
}

JNIEXPORT jint JNICALL Java_com_qoeapps_nativesocks_NativeJavaSockInterface_initializeUdpSocket(JNIEnv *env, jobject jobj) {
    int sfd = socket(AF_INET, SOCK_DGRAM, 0);

    // . . . Handler other socket preparations

    // Convert the character and

    return (jint)sfd;
}
jbyteArray as_byte_array(unsigned char* buf, int len) {
    jbyteArray array = env->NewByteArray (len);
    env->SetByteArrayRegion (array, 0, len, reinterpret_cast<jbyte*>(buf));
    return array;
}

unsigned char* as_unsigned_char_array(jbyteArray array) {
    int len = env->GetArrayLength (array);
    unsigned char* buf = new unsigned char[len];
    env->GetByteArrayRegion (array, 0, len, reinterpret_cast<jbyte*>(buf));
    return buf;
}