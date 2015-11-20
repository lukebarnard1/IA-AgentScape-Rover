package rover;

import org.iids.aos.agent.Agent;
import org.iids.aos.service.ServiceBroker;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for all rovers.
 * This class is not to be modified as it contains the underlying logic across all rovers.
 */
public abstract class Rover extends Agent {


    private static final long serialVersionUID = 1L;

    private IRoverService service;
    private String team;

    private int speed;
    private int scanRange;
    private int maxLoad;

    private boolean started;

    private String clientKey;

    /**
     * the messages received from other agents using either broadcast or directed messages
     */
    protected Set<String> messages;

    private Thread pollThread;

    abstract void begin();

    abstract void end();

    abstract void poll(PollResult pr);


    public Rover() {
        super();
        service = null;
        team = null;
        messages = new HashSet<String>();
        started = false;

        speed = 3;
        scanRange = 3;
        maxLoad = 3;

        clientKey = null;

        pollThread = null;

        // we need to load this class now
        // to avoid a bug in agentscape
        ScanItem si = new ScanItem(1, 0, 0);

    }

    private ServiceBroker sb;

    /**
     * little helper method to bind the roverservice
     */
    private void BindService() {
        //TODO: check if this is still the case as it creates tons of calls
        // which might not be needed

        //we need to bind before each invocation because
        //of a bug in agentscape.
        try {
            service = sb.bind(IRoverService.class);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private boolean inActiveAgent(){
        boolean run = true;

        BindService();
        PollResult pr = null;
        try {
            pr = service.Poll(clientKey);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        if (pr != null) {
            if (pr.getResultType() == PollResult.WORLD_STARTED) {
                //world has started
                started = true;
                //begin
                begin();
            } else if (pr.getResultType() == PollResult.WORLD_STOPPED) {
                // world reset after switching scenarios
                getLog().info("Stopping inActive agent " + clientKey);
                end();
                run = false;
                try {
                    clientKey = service.registerClient(team);
                    //set our attributes with the service
                    service.setAttributes(clientKey, speed, scanRange, maxLoad);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            }
        return run;
    }

    private boolean activeAgent(){
        boolean run  = true;
        BindService();

        PollResult pr = null;
        try {
            pr = service.Poll(clientKey);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        if (pr != null) {
            if (pr.getResultType() == PollResult.WORLD_STOPPED) {
                started = false;

                //call end, the world has ended.
                end();

                try {

                    clientKey = service.registerClient(team);
                    //set our attributes with the service
                    service.setAttributes(clientKey, speed, scanRange, maxLoad);
                } catch (Exception e) {
                    e.printStackTrace();
                }


                   getLog().info("Stopping active agent " + clientKey);

                    run  = false;


            } else {
                poll(pr);
            }
        }
        return run;
    }

    @Override
    public void run() {


        //get the IRoverService
        sb = getServiceBroker();
        try {
            BindService();

            //now register with the service
            clientKey = service.registerClient(team);

            //set our attributes with the service
            service.setAttributes(clientKey, speed, scanRange, maxLoad);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while (true){

            if (started){
                activeAgent();

            } else {
                inActiveAgent();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //set the attributes for this rover.  Must be done before the world starts.
    public void setAttributes(int speed, int scanRange, int maxLoad) throws Exception {

        if (started) {
            throw new Exception("attributes can't be changed after the world has been started");
        }

        if (service != null) {
            //set our attributes with the service
            BindService();
            service.setAttributes(clientKey, speed, scanRange, maxLoad);
        }

        this.speed = speed;
        this.scanRange = scanRange;
        this.maxLoad = maxLoad;
    }

    /**
     * Moves the rover to an offset from its current position
     * @param xOffset directional offset of current position on the X-Axis
     * @param yOffset directional offset of current position on the Y-Axis
     * @param speed movement speed towards destination
     * @throws Exception
     */
    public void move(double xOffset, double yOffset, double speed) throws Exception {
        BindService();
        service.move(clientKey, xOffset, yOffset, speed);
    }

    /**
     * Stops whatever the current task is
     * @throws Exception
     */
    public void stop() throws Exception {
        BindService();
        service.stop(clientKey);
    }

    /**
     * Broadcasts a message to all agents within the current agent's team.
     * @param message the current message to be send to all team mates
     */
    public void broadCastToTeam(String message){
        service.broadCastToTeam(clientKey,message);
    }

    /**
     * Broadcasts a message to a specific agent within the current agent's team.
     * @param remoteUnit the clientKey of the remote unit. The key is constructed in RoverServiceImpl in register
     * @param message the current message to be send to one other unit
     */
    public void broadCastToUnit(String remoteUnit, String message){
        service.broadCastToUnit(clientKey,remoteUnit,message);
    }
    
    /**
     * Called when a message is added to messages.
     * @param message Message received
     */
    protected void receiveMessage(String message) {
        //Do nothing.
    }

    /**
     * retrieves all messages which were send to the current agent. The messages are stores in the messages set.
     */
    public void retrieveMessages(){
        String[] post = service.receiveMessages(clientKey);
        for (String message : post) {
            messages.add(message);
            receiveMessage(message);
        }
    }


    /**
     * Scans for locations of other rovers, bases, and resources
     * @param range Range in world units around the rover
     * @throws Exception
     */
    public void scan(double range) throws Exception {
        BindService();
        service.scan(clientKey, range);
    }

    /**
     * Collects a resource from the world
     * @throws Exception
     */
    public void collect() throws Exception {
        BindService();
        service.collect(clientKey);
    }

    /**
     * Deposits a resource this rover is carrying
     * @throws Exception
     */
    public void deposit() throws Exception {
        BindService();
        service.deposit(clientKey);
    }

    /**
     * Gets the energy/power this rover has remaining
     * @return the energy amount remaining for the current agent
     */
    public double getEnergy() {
        BindService();

        try {
            return service.getEnergy(clientKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    //gets the current number of resources this rover is carrying
    public int getCurrentLoad() {
        BindService();

        try {
            return service.getCurrentLoad(clientKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;

    }

    //The height of the world
    public int getWorldHeight() {
        BindService();
        return service.getWorldHeight();
    }

    //The width of the world
    public int getWorldWidth() {
        BindService();
        return service.getWorldWidth();
    }

    //The total number of resources in the world
    public int getWorldResources() {
        BindService();
        return service.getWorldResources();
    }

    //Returns true if the scenario involves other teams/
    public boolean isWorldCompetitive() {
        BindService();
        return service.isWorldCompetitive();
    }

    //Gets this client's team name
    public String getTeam() {
        return team;
    }

    //Sets the team name
    public void setTeam(String team) {
        this.team = team;
    }

    //Has the world been started
    public boolean IsStarted() {
        return started;
    }

    //Returns the current scenario
    public int getScenario() {
        BindService();
        return service.getScenario();
    }

}
