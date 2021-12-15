import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.util.LinkedList;
import java.util.Queue;

class Process {
    int JobId;
    int ttl; //total time limit
    int tll; //total line limit
    int ttc; //total time count
    int llc; //line limit count

    int PTBR;
    int IC;
    int RA;
    boolean C;
    int trigger;
    char R[];
    //other variables to be added
    int SI;
    int TI;
    int PI;
    //drumLocation - starting address of cards in drum for this process
    //outputLinesCount - total number of outputlines to be printed for this process, increment it on enocuntering a PD instruction
    int drumLocation, outputLinesPrinted, programCardsCount, dataCardsCount, programCardsLoaded, dataCardsLoaded;
    String errorMessage;
    //flag to check GD or SR
    int flag;
    public Process() {
        this.drumLocation = 0;
        this.outputLinesPrinted = 0;
        this.programCardsCount = 0;
        this.programCardsLoaded = 0;
        this.dataCardsCount = 0;
        this.dataCardsLoaded = 0;
        this.JobId = 0;
        this.ttl = 0;
        this.tll = 0;
        this.ttc = 0;
        this.llc = 0;
        this.PTBR = 0;
        this.IC = 0;
        this.RA = 0;
        this.C = false;
        this.trigger = 0;
        this.R = new char[4];
        this.errorMessage = "";
        flag = 0;
    }
}

class BufferStorage {
    int status;
    char F;
    char data[];
    public BufferStorage() {
        this.data = new char[40];
        for(int i = 0; i < 40; i++) {
            this.data[i] = '\0';
        }
        this.status = 0;
    }
}

public class MOS {
    BufferedReader fReader;
    BufferedWriter fWriter;

    private int IOI;
    private int CH1time;
    private int CH2time;
    private int CH3time;
    private int CH1busy;
    private int CH2busy;
    private int CH3busy;
    private int globalTimer;
    private int drumTrackCounter;
    private char F;
    private char drumStorage[][];
    private char M[][];
    private boolean flag[];
    private char IR[];
    //private char R[];
    //private int IC; 
    private char buffer[];
    //private int trigger;
    Queue<Process> loadQueue, readyQueue, inputOutputQueue, terminateQueue;
    Queue<BufferStorage> emptyBufferQueue, inputBufferQueue, outputBufferQueue;
    Process currentProcessCPU, currentProcessLoading;
    MOS() {
        this.IOI = 1;
        this.CH1time = 5;
        this.CH2time = 5;
        this.CH3time = 2;
        this.CH1busy = 1;
        this.CH2busy = 0;
        this.CH3busy = 0;
        this.globalTimer = 1;
        this.drumTrackCounter = 0;
        this.F = 'p';
        this.drumStorage = new char[150][40];
        this.M = new char[300][4];
        this.IR = new char[4];
        //this.R = new char[4];
        //this.IC = 0;
        this.buffer = new char[40];
        this.flag = new boolean[30];
        //this.trigger = 0;
        this.loadQueue = new LinkedList<Process>();
        this.readyQueue = new LinkedList<Process>();
        this.inputOutputQueue = new LinkedList<Process>();
        this.terminateQueue = new LinkedList<Process>();
        this.emptyBufferQueue = new LinkedList<BufferStorage>();
        this.inputBufferQueue = new LinkedList<BufferStorage>();
        this.outputBufferQueue = new LinkedList<BufferStorage>();
        this.currentProcessCPU = null;
        this.currentProcessLoading = null;
        try {
            this.fReader = new BufferedReader(new FileReader("G:\\MOS\\src\\input.txt"));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MOS.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            this.fWriter = new BufferedWriter(new FileWriter("G:\\MOS\\src\\output1.txt"));
        } catch (IOException ex) {
            Logger.getLogger(MOS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void createSupervisoryStorage() {
        BufferStorage bObject = new BufferStorage();
        int x = 12; //number of blocks  in supervisory storage
        for(int i = 0; i < x; i++) {
            //BufferStorage bObject = new BufferStorage();
            bObject.status = i;
            emptyBufferQueue.add(bObject);
            bObject = new BufferStorage();
        }
    }

    void startOS() throws IOException {
        System.out.println("Starting OS");
        while(fReader.ready() || !inputBufferQueue.isEmpty() || !outputBufferQueue.isEmpty() || !terminateQueue.isEmpty() || !loadQueue.isEmpty() || !readyQueue.isEmpty() || !inputOutputQueue.isEmpty())
        { //this value is for debugging, write condition for termination here
            //System.out.println("Calling simulate");
            simulate();
            if(currentProcessCPU != null && currentProcessCPU.flag == 1) {
                currentProcessCPU.flag = 0;
            }
            else {
                //System.out.println("Calling Start Execution");
                startExecution();
            }
            startChannels();
        }
    }

    void simulate() throws IOException {
        //function to simulate time, called at the end of each time unit
        if(CH1busy == 1) {
            CH1time--;
        }
        if(CH2busy == 1) {
            CH2time--;
        }
        if(CH3busy == 1) {
            CH3time--;
        }
        System.out.println("Universal Timer: "+globalTimer);
        globalTimer++;
        if(currentProcessCPU != null ) {
            currentProcessCPU.ttc++;
        }
    }

    void startExecution() throws IOException {
        //System.out.println(readyQueue.peek());
        if(!readyQueue.isEmpty()) {
            Process ready = readyQueue.peek();
            currentProcessCPU = ready;
            System.out.println("JobId: "+ready.JobId+" is executing");
            if(ready.PTBR == -1) { //if page table creation has failed, terminate
                System.out.println("Page table not created");
                ready.errorMessage = "Page table creation failed";
                terminateQueue.add(ready);
                readyQueue.poll();
                return;
            }
            //Address Map - result in ready.RA
            //System.out.print("Next instn add mapping: ");
            //System.out.println("IC: "+ready.IC);
            AddressMap(ready.IC, ready); //map next program instruction address to Real Address
            //System.out.println("RA: "+ready.RA);
            //System.out.println("Memory: "+M[ready.RA][0]+""+M[ready.RA][1]+""+M[ready.RA][2]+""+M[ready.RA][3]);
            if(ready.PI != 0) {
                MasterMode(ready);
                return;
            }

            //real address obtained, execute instruction
            for(int i = 0; i<4; i++) {
                IR[i] = M[ready.RA][i];
                //System.out.print(IR[i]);
            }
            //System.out.println();
            if(IR[0] == 'H') {
                //call master mode to check errors in execution based on PI, TI - move to terminate queue
                ready.SI = 3;
                MasterMode(ready);
                return;
            }
            //System.out.println(IR[2]+""+IR[3]);
            checkOperand(IR[2], IR[3], ready);
            if(ready.PI != 0) {
                //operand error has occured, call master mode and move process to terminate queue
                MasterMode(ready);
                return;
            }
            int operand = (IR[2]-48)*10+(IR[3]-48);
            AddressMap(operand, ready);
            //if address map is unsuccesful then page fault
            if(ready.PI != 0) { //page fault has occurred, call master mode and check if it is valid or invalid

                //System.out.println("SI: "+ready.SI+" PI: "+ready.PI+" TI: "+ready.TI);
                MasterMode(ready);
                if(ready.PI != 0)
                    return;
                else
                    AddressMap(operand, ready);
            }
            String operation = IR[0]+""+IR[1];
            //System.out.println("Instruction: "+operation);
            if(operation.equals("GD")) {
                //move process to IOqueue
                //channel 3 will directly load data at process.RA
                ready.IC++; //next instruction
                //ready.ttc++; 
                ready.flag = 1;
                ready.SI = 1;
                //inputOutputQueue.add(ready);
                //readyQueue.poll();
                //call Master Mode - TI=0 and SI=1 will execute GD
                MasterMode(ready);
                return;
            }
            else if (operation.equals("PD")) {
                //move process to IOqueue
                //channel 3 will directly print data at process.RA
                ready.IC++;
                ready.SI = 2;
                //ready.ttc++;
                //inputOutputQueue.add(ready);
                //readyQueue.poll();
                //call Master Mode - TI=0 and SI=2 will execute PD
                MasterMode(ready);
                return;
            }
            else if (operation.equals("LR")) {
                //lr
                ready.IC++;
                ready.R[0] = M[ready.RA][0];
                ready.R[1] = M[ready.RA][1];
                ready.R[2] = M[ready.RA][2];
                ready.R[3] = M[ready.RA][3];
            }
            else if (operation.equals("SR")) {
                //sr
                ready.IC++;
                ready.flag = 1;
                M[ready.RA][0] = ready.R[0];
                M[ready.RA][1] = ready.R[1];
                M[ready.RA][2] = ready.R[2];
                M[ready.RA][3] = ready.R[3];
            }
            else if(operation.equals("CR")) {
                //cr
                ready.IC++;
                int count=0;
                for(int j=0;j<=3;j++)
                {    if(M[ready.RA][j] == ready.R[j])
                {      count++;

                }
                }
                if(count==4)
                    ready.C=true;
            }
            else if (operation.equals("BT")) {
                //bt
                ready.IC++;
                if(ready.C == true)
                {
                    int i = IR[2]-'0';
                    i = i*10 + (IR[3] - '0');
                    ready.IC = i;
                    //System.out.println(ready.IC);
                }
            }
            else{
                ready.PI = 1;
                //System.out.println("Oh");
            }


            //ready.ttc++;
            if(ready.ttc+1 == ready.ttl )  //Simulation
            {
                ready.TI=2;
            }
            if(ready.PI!=0 || ready.TI!=0)
            {
                MasterMode(ready);
                return;
            }
            if(ready.SI!=0){
                MasterMode(ready);
                if(ready.trigger==1)
                    return;
            }
        } else {
            if(currentProcessCPU != null) {
                currentProcessCPU.ttc--;
            }
            //System.out.println("Nothing to execute for CPU");
        }
    }

    void startChannels() throws IOException {
        //start channels based on IOI
        switch(IOI) {
            case 1: //cal IR1
                if(CH1time == 0) {
                    //System.out.println("Calling channel 1");
                    channel1();
                    if(!fReader.ready()) {
                        IOI ^= 1<<0;
                        CH1busy = 0;
                    }
                    CH1time = 5;
                }
                break;
            case 2: //call IR2
                if(CH2time == 0) {
                    //System.out.println("Calling channel 2");
                    channel2();
                    if(outputBufferQueue.isEmpty()) {
                        IOI ^= 1<<1;
                        CH2busy = 0;
                    }
                    CH2time = 5;
                }
                break;
            case 3: //call IR1, IR2
                if(CH1time == 0) {
                    //System.out.println("Calling channel 1");
                    channel1();
                    if(!fReader.ready()) {
                        IOI ^= 1<<0;
                        CH1busy = 0;
                    }
                    CH1time = 5;
                }
                if(CH2time == 0) {
                    //System.out.println("Calling channel 2");
                    channel2();
                    if(outputBufferQueue.isEmpty()) {
                        IOI ^= 1<<1;
                        CH2busy = 0;
                    }
                    CH2time = 5;
                }
                break;
            case 4: //call IR3
                if(CH3time == 0) {
                    //System.out.println("Calling channel 3");
                    channel3();
                    CH3time = 2;
                }
                break;
            case 5: //call IR1, IR3
                if(CH1time == 0) {
                    //System.out.println("Calling channel 1");
                    channel1();
                    if(!fReader.ready()) {
                        IOI ^= 1<<0;
                        CH1busy = 0;
                    }
                    CH1time = 5;
                }
                if(CH3time == 0) {
                    //System.out.println("Calling channel 3");
                    channel3();
                    CH3time = 2;
                }
                break;
            case 6: //call IR2, IR3
                if(CH2time == 0) {
                    //System.out.println("Calling channel 2");
                    channel2();
                    if(outputBufferQueue.isEmpty()) {
                        IOI ^= 1<<1;
                        CH2busy = 0;
                    }
                    CH2time = 5;
                }
                if(CH3time == 0) {
                    //System.out.println("Calling channel 3");
                    channel3();
                    CH3time = 2;
                }
                break;
            case 7: //call IR1, IR2, IR3
                if(CH1time == 0) {
                    //System.out.println("Calling channel 1");
                    channel1();
                    if(!fReader.ready()) {
                        IOI ^= 1<<0;
                        CH1busy = 0;
                    }
                    CH1time = 5;
                }
                if(CH2time == 0) {
                    //System.out.println("Calling channel 2");
                    channel2();
                    if(outputBufferQueue.isEmpty()) {
                        IOI ^= 1<<1;
                        CH2busy = 0;
                    }
                    CH2time = 5;
                }
                if(CH3time == 0) {
                    //System.out.println("Calling channel 3");
                    channel3();
                    CH3time = 2;
                }
                break;
        }
    }

    void channel1() throws IOException {
        //execute only if time of previous task of channel1 is over
        //function to read from input file to supervisory storage

        //use it to read line by line
        //save line to first block of supervisory storage [in the first object of ebq], mark status of that block as ifb status = 1, add block to inputbufferfullqueue remove from ebq. 
        //1. If the card has $AMJ, create an object of class process and (store and initialize pcb and other variables, Allocate a frame for page table, Initialise PTR and page table, set F to P which indicates that program cards are followed by this control card. return buffer to emptybufferqueue.
        // 2. if the card is $DTA set F to D which means following cards will be data cards. change the status of inputfullbuffer to emptybuffer. return the buffer to emptybufferqueue.
        // 3. if the card is $END then place the PCB on load queue. change the status of inputfullbuffer to emptybuffer. return the buffer to emptybufferqueue.
        // 4. for all cases other than the ones mentined above, place the inputfullbuffer on inputfullbufferqueue
        String card;
        BufferStorage eb = emptyBufferQueue.peek();
        //getline(fReader, card);
        card = fReader.readLine();
        for(int i = 0; i < card.length(); i++) {
            eb.data[i] = card.charAt(i);
        }
        if(eb.data[0] == '$' && eb.data[1] == 'A' && eb.data[2] == 'M' && eb.data[3] == 'J') {
            F = 'P';
            currentProcessLoading = new Process();
            //inilialize values of PCB, PTR for Process
            String temp = card.substring(4, 8);
            currentProcessLoading.JobId = Integer.parseInt(temp);
            System.out.println("Channel 1: AMJ for JobId: "+currentProcessLoading.JobId);
            temp = card.substring(8,12);
            currentProcessLoading.ttl = Integer.parseInt(temp);
            temp = card.substring(12,16);
            currentProcessLoading.tll = Integer.parseInt(temp);
            currentProcessLoading.drumLocation = drumTrackCounter;
            //initializeProcess(currentProcessLoading); - done in constructor already
            drumTrackCounter++;
            createPageTable();
            // System.out.println(currentProcessLoading.JobId);
            // System.out.println(currentProcessLoading.ttl);
            // System.out.println(currentProcessLoading.tll);
            // System.out.println(currentProcessLoading.drumLocation);
            // System.out.println(F);
            reinitializeEmptyBuffer(eb);
        }
        else if (eb.data[0] == '$' && eb.data[1] == 'D' && eb.data[2] == 'T' && eb.data[3] == 'A') {
            System.out.println("Channel 1: DTA for JobId: "+currentProcessLoading.JobId);
            F = 'D';
            reinitializeEmptyBuffer(eb);
        }
        else if (eb.data[0] == '$' && eb.data[1] == 'E' && eb.data[2] == 'N' && eb.data[3] == 'D') {
            System.out.println("Channel 1: END for JobId: "+currentProcessLoading.JobId);
            drumTrackCounter+= currentProcessLoading.tll; //save locations for storing output lines
            drumTrackCounter+= 1; //one extra location to store error message - make sure the error message doesnt exceed 40 characters
            loadQueue.add(currentProcessLoading); //all cards have been loaded on the drum so now add to load queue, processes in loadqueue are transferred to main memory for execution
            reinitializeEmptyBuffer(eb); //return empty buffer
        }
        else {
            //System.out.println("Program/Data card");
            eb.status = 1;
            eb.F = F;
            if(eb.F == 'P') {
                System.out.println("Channel 1: Program Card loading");
                //currentProcessLoading.programCardsCount++;
            } else if ( eb.F == 'D') {
                System.out.println("Channel 1: Data Card Loading");
                //currentProcessLoading.dataCardsCount++;
            }
            inputBufferQueue.add(eb);
            if((IOI & 4) == 0) { //check if channel 3 is busy, if not then flip bit for channel 3 in IOI and make channel 3 busy
                IOI ^= 1<<2;
                CH3busy = 1;
            }
            emptyBufferQueue.poll();
            drumTrackCounter++; //increment counter for keeping a track of the next free track in the drum
            //following code for debugging only
            // struct BufferStorage *ifb = inputBufferQueue.front();
            // System.out.println(ifb.F);
            // System.out.println(ifb.status);
            // for(int i = 0; i < 40; i++) {
            //     System.out.println(ifb.data[i];
            // }
            // System.out.println(endl;
            // inputBufferQueue.pop();
        }
    }

    void channel2() throws IOException {
        //execute only if time of previous task of channel2 is over
        //function to write from supervisory storage [ofbq objects] to output file
        //print from outputbufferqueue to output file, change status from ofb to eb, return buffer to emptybufferqueue. if more entires are there in outputbufferqueue execute channel2 in the next cycle for the next ofb.
        if(!outputBufferQueue.isEmpty()) {
            BufferStorage ob = outputBufferQueue.peek();
            String outputline = new String(ob.data);
            fWriter.write(outputline+"\n");
            System.out.print("Channel 2: ");
            System.out.println("Output Line to File- "+outputline);
            outputBufferQueue.poll();
            reinitializeEmptyBuffer(ob);
            emptyBufferQueue.add(ob);
        }
    }

    void channel3() throws IOException {
        //System.out.println("Stuck HERE!!!!!");
        //////condition for changing status of IOI for channel 3 reamaining
        //execute only if time of previous task of channel3 is over
        //5 jobs for channel3, execute based on priority
        //i..get input from supervisory storage to drum if ifbqueue is not empty, save the tracknumber in P or D part of PCB based on the value of F. change the status of the buffer from inputfullbuffer to emptybuffer, return it to emptybufferqueue.
        //ii..output line from given track to an eb, make it ofb and add to outputbufferqueue.
        //iii..if process is in load queue, load program cards from track(drum) to main memory, decrement count in PCB for each card loaded, then when 0 place PCB on ready queue. 
        //iv..If any process is in the inputoutputqueue for GD, read data card from given track to main memory, decrement count in PCB,move PCB to ready queue and set TSC(time slice) to 0.
        //v..If any process is in the inputoutputqueue for PD, write data from main memory to given track, increment TLC in PCB, if TI= 2 or 3, move PCB to TQ
        if(!inputBufferQueue.isEmpty()) {
            //task i.
            //for P place in 2,3 location of PTR and for D place in 0,1 locations
            //System.out.println("Moving data from Card reader to supervisory storage");
            //System.out.println("Current Process: "<<currentProcessLoading.JobId);
            //System.out.println("Drum track coutner: "<<drumTrackCounter);
            //System.out.println("Current Process drum location: "<<currentProcessLoading.drumLocation);
            BufferStorage ifb = inputBufferQueue.peek();
            //System.out.println("i: "+ifb.F);
            System.out.println("Channel 3 Case 1(C->S) "+ifb.F+" for JobId: "+currentProcessLoading.JobId);
            if(ifb.F == 'P') {
                //place on apt track number
                //currentProcessLoading.drumLocation
                //if programCardsLoaded > 10 ??? How to save entries to page table??
                int location = currentProcessLoading.drumLocation + currentProcessLoading.programCardsCount;

                //System.out.println("Job ID : "+currentProcessLoading.JobId);
                //System.out.println("Drum Location : "+currentProcessLoading.drumLocation);
                //System.out.println("Program Card Count : "+currentProcessLoading.programCardsCount);

                //System.out.println("Data being transffered : ";
                System.out.print("Program card store to Drum location "+location+" : ");
                for(int i = 0; i < 40; i++) {
                    drumStorage[location][i] = ifb.data[i];
                    System.out.print(ifb.data[i]);
                }
                System.out.println();
                reinitializeEmptyBuffer(ifb);
                inputBufferQueue.poll();
                emptyBufferQueue.add(ifb);
                currentProcessLoading.programCardsCount++;
            }
            if(ifb.F == 'D') {
                //place on apt track number
                //currentProcessLoading.drumLocation
                //if dataCardsLoaded > 10 ??? How to save entries to page table??
                int location = currentProcessLoading.drumLocation + currentProcessLoading.programCardsCount + currentProcessLoading.dataCardsCount;
                //System.out.println("Location : "<<location);
                //System.out.println("Data being transffered : ";
                System.out.print("Data card stored to Drum location "+location+" : ");
                for(int i = 0; i < 40; i++) {
                    drumStorage[location][i] = ifb.data[i];
                    System.out.print(ifb.data[i]);
                }
                System.out.println();
                reinitializeEmptyBuffer(ifb);
                inputBufferQueue.poll();
                emptyBufferQueue.add(ifb);
                currentProcessLoading.dataCardsCount++;
            }
        }
        else if (!terminateQueue.isEmpty()) {
            //task ii.
            //System.out.println("Moving Output lines from drum to supervisory storage");

            Process terminateProcess = terminateQueue.peek();
            System.out.println("Channel 3 Case 2(D->S) for JobId: "+terminateProcess.JobId);
            if(terminateProcess.outputLinesPrinted < terminateProcess.llc) {
                //copy output line on empty buffer and mark as ofb, move to outputfullbufferqueue
                BufferStorage output = emptyBufferQueue.peek();
                int location = terminateProcess.drumLocation + terminateProcess.programCardsCount + terminateProcess.dataCardsCount + terminateProcess.outputLinesPrinted; //maybe add -1 to this expression, debug and check not sure
                //System.out.println("Location : "+location);
                //System.out.print("Data sent: ");
                for(int i = 0; i < 40; i++ ){
                    output.data[i] = drumStorage[location][i];
                    //System.out.print(drumStorage[location][i]);
                }
                //System.out.println();
                output.status = 2;
                emptyBufferQueue.poll();
                outputBufferQueue.add(output);
                terminateProcess.outputLinesPrinted++;
                //start channel 2 if it is off :- if (!(IOI & 2)) then IOI ^= 1<<1
                if((IOI & 2) == 0) {
                    IOI ^= 1<<1;
                    CH2busy = 1;
                }
            } else if(terminateProcess.outputLinesPrinted == terminateProcess.llc) {
                //formulate error message based on PI and TI values, copy it to an empty buffer and mark as ofb, remove process from terminate queue, free memory
                printMainMemory();
                //printDrumStorage();
                BufferStorage em = emptyBufferQueue.peek();
                String s;
                s = terminateProcess.errorMessage;
                System.out.println(s);
                for(int i = 0; i < min(s.length(),40); i++ ){
                    em.data[i] = s.charAt(i);
                }
                em.status = 2;
                emptyBufferQueue.poll();
                outputBufferQueue.add(em);
                BufferStorage output = emptyBufferQueue.peek();
                // System.out.print("JId:"+terminateProcess.JobId);
                // System.out.print(" IC:"+terminateProcess.IC);
                // //System.out.print(" IR:"+terminateProcess.JobId);
                // System.out.print(" TTC:"+terminateProcess.ttc);
                // System.out.print(" LLC:"+terminateProcess.llc);
                // System.out.print(" TTL:"+terminateProcess.ttl);
                // System.out.print(" TLL:"+terminateProcess.tll);
                s = "JId:"+terminateProcess.JobId+" IC:"+terminateProcess.IC+" TTC:"+terminateProcess.ttc+" LLC:"+terminateProcess.llc+" TTL:"+terminateProcess.ttl+" TLL:"+terminateProcess.tll+"\n\n";
                System.out.println("JId:"+terminateProcess.JobId+" IC:"+terminateProcess.IC+" TTC:"+terminateProcess.ttc+" LLC:"+terminateProcess.llc+" TTL:"+terminateProcess.ttl+" TLL:"+terminateProcess.tll);
                //System.out.println("-------------------------------------------------------------------------------");
                for(int i = 0; i < min(s.length(),40); i++ ){
                    output.data[i] = s.charAt(i);
                }
                //System.out.println();
                output.status = 2;
                emptyBufferQueue.poll();
                outputBufferQueue.add(output);
                for(int i = 0; i < 10; i++) {
                    String pn = M[terminateProcess.PTBR+i][2]+""+M[terminateProcess.PTBR+i][3];
                    //System.out.println(Integer.parseInt(pn)*10);
                    checkOperand(M[terminateProcess.PTBR+i][2], M[terminateProcess.PTBR+i][3], terminateProcess);
                    if(terminateProcess.PI == 0) {
                        //System.out.print(pn);
                        int pageNo = Integer.parseInt(pn);
                        for(int j = pageNo*10; j < pageNo*10 + 10; j ++) {
                            M[j][0] = ' ';
                            M[j][1] = ' ';
                            M[j][2] = ' ';
                            M[j][3] = ' ';
                        }
                        flag[pageNo] = false;
                    }
                    M[terminateProcess.PTBR+i][0] = ' ';
                    M[terminateProcess.PTBR+i][1] = ' ';
                    M[terminateProcess.PTBR+i][2] = ' ';
                    M[terminateProcess.PTBR+i][3] = ' ';
                }
                flag[terminateProcess.PTBR/10]  = false;
                //terminateProcess.outputLinesPrinted++;
                terminateQueue.poll();
            }
        }
        else if (!loadQueue.isEmpty()) {
            //task iii.
            //load program cards to main memory at a random block location, save location in page table
            //if all program cards loaded then put process in ready queue
            //drum . main memory
            Process load = loadQueue.peek();
            System.out.println("Channel 3 Case 3 (D->M) for JobId: "+load.JobId);
            int pageNo = allocate();
            while(flag[pageNo]){
                pageNo = allocate();
            }
            flag[pageNo] = true;
            // for(int i = 0; i < 30; i++) {
            //     System.out.print(flag[i]+" ");
            // }
            //System.out.println();
            //update page table
            int pagePTR = load.PTBR;
            //pagePTR++;
            pagePTR+=load.programCardsLoaded;
            M[pagePTR][0] = ' ';
            M[pagePTR][1] = ' ';
            M[pagePTR][2] = (char)((pageNo/10)+48);
            M[pagePTR][3] = (char)((pageNo%10)+48);
            int pageStart = pageNo*10;
            int k = 0;
            int location = load.drumLocation + load.programCardsLoaded;
            //System.out.println("Number of Program Cards: "+load.programCardsCount);
            //System.out.println("Program Card being loaded: "+load.programCardsLoaded);
            //System.out.print("Program card being loaded to Main memory: ");
            for(int i =pageStart;i<pageStart+10;i++){
                for(int j = 0;j<4;j++){
                    if(drumStorage[location][k]=='H' && drumStorage[location][k+1]!=' ')
                    {   //j = 0;

                        //System.out.print(drumStorage[location][k]);
                        M[i][j] = drumStorage[location][k++];
                        i++;
                        j = -1;
                    }
                    else {
                        //System.out.print(drumStorage[location][k]);
                        M[i][j] = drumStorage[location][k++];
                    }
                }
            }
            //System.out.println();
            load.programCardsLoaded++;
            if(load.programCardsLoaded == load.programCardsCount) {
                readyQueue.add(load);
                loadQueue.poll();
            }
            //following code only for debugging channels
            //System.out.println("Moving process to ready queue");
            // System.out.println("iii");
            // readyQueue.add(loadQueue.peek());
            // loadQueue.poll();
        }
        else if (!inputOutputQueue.isEmpty()) {
            //based on GD or PD perform task iv. or v.
            //for accessing data cards use process.drumLocation + process.programCardsCount
            //for accessing blocks to store output data use process.drumLocation + process.programCardsCount + process.dataCardsCount (maybe -1)
            //System.out.println("Executing GD/PD");
            Process inputOutput = inputOutputQueue.peek();
            if(inputOutput.SI == 1) { //read
                if(inputOutput.ttc >= inputOutput.ttl) {
                    inputOutput.IC -= 1;
                    System.out.println("Sending JobID:"+inputOutput.JobId+" to terminate queue");
                    saveErrorMessage(3, 0, inputOutput);
                    terminateQueue.add(inputOutput);
                    inputOutputQueue.poll();
                    return;
                }
                System.out.println("Channel 3 Case 4 for JobId: "+inputOutput.JobId);
                int location = inputOutput.drumLocation + inputOutput.programCardsCount + inputOutput.dataCardsLoaded;
                int k  = 0;
                //System.out.print("GD is saving : ");
                if(inputOutput.dataCardsLoaded >= inputOutput.dataCardsCount) {
                    //terminate(1,0);
                    System.out.println("Sending JobID:"+inputOutput.JobId+" to terminate queue");
                    saveErrorMessage(1, 0, inputOutput);
                    inputOutput.trigger = 1;
                    terminateQueue.add(inputOutput);
                    inputOutputQueue.poll();
                    return;
                }
                for(int i = inputOutput.RA ; i< inputOutput.RA+10 ; i++){
                    for(int j = 0 ; j<4 ; j++){
                        M[i][j] = drumStorage[location][k++];
                        //System.out.print(M[i][j]);
                    }
                }
                inputOutput.dataCardsLoaded++;
                //System.out.println();
                inputOutput.SI = 0;
                readyQueue.add(inputOutput);
                inputOutputQueue.poll();
            }
            else if (inputOutput.SI == 2) { //write
                if(inputOutput.ttc >= inputOutput.ttl) {
                    inputOutput.IC -= 1;
                    System.out.println("Sending JobID:"+inputOutput.JobId+" to terminate queue");
                    saveErrorMessage(3, 0, inputOutput);
                    terminateQueue.add(inputOutput);
                    inputOutputQueue.poll();
                    return;
                }
                System.out.println("Channel 3 Case 5 for JobId: "+inputOutput.JobId);
                inputOutput.llc++;
                int k =0;
                if(inputOutput.llc > inputOutput.tll) {
                    //terminate(2,0);
                    System.out.println("Sending JobID:"+inputOutput.JobId+" to terminate queue");
                    saveErrorMessage(2, 0, inputOutput);
                    inputOutput.trigger = 1;
                    terminateQueue.add(inputOutput);
                    inputOutputQueue.poll();
                    return;

                }
                int location = inputOutput.drumLocation + inputOutput.programCardsCount + inputOutput.dataCardsCount +inputOutput.llc - 1;
                //System.out.print("PD is saving : ");
                for(int i = inputOutput.RA ; i < inputOutput.RA+10 ; i++){
                    for(int j = 0 ; j<4 ; j++){
                        drumStorage[location][k]  =  M[i][j];
                        //this.bw.write(buffer[k]);
                        //System.out.print(drumStorage[location][k]);
                        k++;
                    }
                }
                //System.out.println();
                //inputOutput.llc++;
                if(inputOutput.TI != 0) {
                    //terminate
                    System.out.println("Sending JobID:"+inputOutput.JobId+" to terminate queue");
                    saveErrorMessage(3, 0, inputOutput);
                    terminateQueue.add(inputOutput);
                    inputOutputQueue.poll();
                } else  {
                    //move back to ready queue
                    inputOutput.SI = 0;
                    readyQueue.add(inputOutput);
                    inputOutputQueue.poll();
                }
            }
        }
        else {
            //no action to perform for channel 3, mark it as busy
            //CH3time = 2;
            //CH3busy = 0;
            //IOI ^= 1<<2;
            return;
        }
    }

    void reinitializeEmptyBuffer(BufferStorage buffer) {
        //System.out.println("Adding buffer back to eb(q)");
        buffer.status = 0;
        for(int i = 0; i< 40; i++) {
            buffer.data[i] = '\0';
        }
    }

    int allocate(){
        Random random = new Random();
        return random.nextInt(29);
    }
    void oldcreatePageTable(){

        currentProcessLoading.PTBR = allocate()*10;
        //System.out.println("Creating Page Table at "+currentProcessLoading.PTBR+" for JobId: "+currentProcessLoading.JobId);
        for(int i = currentProcessLoading.PTBR; i<currentProcessLoading.PTBR+10;i++){
            for(int j = 0;j<4;j++){
                this.M[i][j] = '#';
            }
        }
        flag[currentProcessLoading.PTBR/10] = true;
    }

    void createPageTable(){
        int pageNo = allocate();
        while(flag[pageNo]){
            pageNo = allocate();
        }

        currentProcessLoading.PTBR = pageNo*10;
        System.out.println("Creating Page Table at "+currentProcessLoading.PTBR+" for JobId: "+currentProcessLoading.JobId);
        for(int i = currentProcessLoading.PTBR; i<currentProcessLoading.PTBR+10;i++){
            for(int j = 0;j<4;j++){
                this.M[i][j] = '#';
            }
        }
        flag[currentProcessLoading.PTBR/10] = true;
    }

    void AddressMap(int VA, Process obj){
        //System.out.println("Mapping VA to RA");
        int i = 0 , pte ;
        pte = obj.PTBR+ (int)VA/10;
        String temp="";
        for(i = 0 ;i<4;i++)
        {
            temp+=M[pte][i];
        }
        //System.out.println(temp);
        temp = temp.replaceAll("\\s", "");
        if(temp.equals("####") || temp.equals(""))
        {
            obj.PI = 3;//Page Fault
            //System.out.println("Page Fault");
            //cout<<"page fault for VA : "<<VA<<endl;
            return;
        }
        int MPTE = Integer.parseInt(temp);

        obj.RA = MPTE*10 + VA%10;
        //      System.out.println(RA);
    }

    void MasterMode(Process obj) throws IOException{
        //System.out.println("Master Mode. JobId: "+obj.JobId);
        if(obj.TI == 0) {
            // CASE TI=0 AND PI
            if(obj.PI == 1) {
                //terminate(4,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(4, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.PI == 2) {
                //terminate(5,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(5, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.PI ==3) { // page faults
                String operation = IR[0]+""+IR[1];
                if(operation.equals("PD") || operation.equals("LR") || operation.equals("CR")) /*invalid page fault*/ {
                    //terminate(6,0);
                    System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                    saveErrorMessage(6, 0, obj);
                    terminateQueue.add(obj);
                    readyQueue.poll();
                }
                else { //Valid Page Fault
                    int pageNo = allocate();
                    // System.out.print("Flag Array: ");
                    // for(int i = 0; i < 30; i++)
                    //     System.out.print(flag[i]+" ");
                    // System.out.println();
                    //System.out.println("Random no generated: "+pageNo);
                    while(flag[pageNo]) {
                        //System.out.println("Location is full");
                        pageNo = allocate();
                        //System.out.print("Random no generated: "+pageNo);
                    }
                    flag[pageNo] = true;
                    int pagePTR = obj.PTBR;
                    //System.out.println("Page PTR: "+pagePTR);
                    while(M[pagePTR][2] >='0' &&  M[pagePTR][2] <='9'){
                        pagePTR++;
                        //System.out.println("Page PTR: "+pagePTR);
                    }

                    M[pagePTR][0] = ' ';
                    M[pagePTR][1] = ' ';
                    M[pagePTR][2] = (char)((pageNo/10)+48);
                    M[pagePTR][3] = (char)((pageNo%10)+48);

                    //obj.IC--;
                    obj.PI = 0;
                    //ExecuteSlaveMode();           
                    //calling return instead of execute slave mode. because next instruction will be executed in the next cycle
                    return;
                }
            }
            // CASE TI=0 AND SI
            else if(obj.SI == 1) { //GD
                System.out.println("Add JobID:"+obj.JobId+" to IO(q) for GD");
                inputOutputQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.SI == 2) { //PD
                System.out.println("Add JobID:"+obj.JobId+" to IO(q) for PD");
                inputOutputQueue.add(obj);
                readyQueue.poll();
            }
            else {
                //terminate(0,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(0, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }

        }
        else {
            // CASE TI=2 AND PI
            if(obj.PI == 1) {
                //terminate(3,4);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(3, 4, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.PI == 2) {
                //terminate(3,5);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(3, 5, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.PI ==3) {
                //terminate(3,0);    
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(3, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            // CASE TI=2 AND SI
            else if(obj.SI == 1) {
                //terminate(3,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(3, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.SI == 2)
            {
                //write();
                System.out.println("Add JobID:"+obj.JobId+" to IO(q) for PD+terminate");
                inputOutputQueue.add(obj);
                //terminate(3,0);    
                //saveErrorMessage(3, 0, obj);
                //terminateQueue.add(obj);
                readyQueue.poll();
            }
            else if(obj.SI == 3) {
                //terminate(0,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(0, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
            else {
                //terminate(3,0);
                System.out.println("Sending JobID:"+obj.JobId+" to terminate queue");
                saveErrorMessage(3, 0, obj);
                terminateQueue.add(obj);
                readyQueue.poll();
            }
        }
    }

    void checkOperand(char a, char b, Process obj) {
        //System.out.println("Checking operand for the instruction");
        if(!( ( (a<='9'&&a>='0') || a==' ') && ( (b<='9'&&b>='0')|| b==' ') )){
            obj.PI = 2;
            //System.out.println("Operand");
        }


    }

    void saveErrorMessage(int EM1, int EM2, Process obj) {
        System.out.println("Saving termination status for JobId: "+obj.JobId);
        String temp = "Terminated due to ";
        if(EM1 == 0) {
            obj.errorMessage = "Terminated Successfully";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else if(EM1 == 1) {
            obj.errorMessage = temp+" Out of Data Error";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else if(EM1 == 2) {
            obj.errorMessage = temp+" Line Limit Error";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else if(EM1 == 6) {
            obj.errorMessage = temp+" Invalid Page Fault";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else if(EM1 == 4) {
            obj.errorMessage = temp+" Operation Code Error";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else if(EM1 == 5) {
            obj.errorMessage = temp+" Operand Error";
            int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
            for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                drumStorage[location][i] = obj.errorMessage.charAt(i);
            }
        }
        else {
            String temp2 = "Time Limit Exceeded Error & ";
            if(EM2 == 0) {
                obj.errorMessage = temp+" Time Limit Exceeded Error";
                int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
                for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                    drumStorage[location][i] = obj.errorMessage.charAt(i);
                }
            }
            else if(EM2 == 4) {
                obj.errorMessage = temp+" "+temp2+" Operation Code Error";
                int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
                for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                    drumStorage[location][i] = obj.errorMessage.charAt(i);
                }
            }
            else {
                obj.errorMessage = temp+" "+temp2+" Operand Error";
                int location = obj.drumLocation + obj.programCardsCount + obj.dataCardsCount + obj.tll;
                for(int i = 0; i < min(obj.errorMessage.length(),40); i++) {
                    drumStorage[location][i] = obj.errorMessage.charAt(i);
                }
            }
        }
    }

    int min(int a, int b) {
        return a>b ? b : a;
    }

    void printDrumStorage() throws IOException {
        for(int i = 0; i < 120; i++) {
            System.out.print(i+": ");
            for(int j = 0; j < 40; j++) {
                System.out.print(drumStorage[i][j]+" ");
            }
            System.out.println();
        }
    }

    void printMainMemory() throws IOException {
        for(int i = 0; i < 30; i++) {
            for(int j = 0; j < 10; j ++) {
                int location = i*10+j;
                System.out.print(location+": ");
                System.out.print(M[location][0]+" ");
                System.out.print(M[location][1]+" ");
                System.out.print(M[location][2]+" ");
                System.out.print(M[location][3]+" ");
                System.out.print("\t");
            }
            System.out.println();
        }
    }


    public static void main(String[] args) throws IOException {
        MOS mos = new MOS();
        mos.createSupervisoryStorage();
        mos.startOS();
        mos.fReader.close();
        mos.fWriter.close();
        mos.printDrumStorage();
        //mos.printMainMemory();
    }
}
