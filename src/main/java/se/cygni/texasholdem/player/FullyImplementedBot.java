package se.cygni.texasholdem.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.cygni.texasholdem.client.ClientEventDispatcher;
import se.cygni.texasholdem.client.CurrentPlayState;
import se.cygni.texasholdem.client.PlayerClient;
import se.cygni.texasholdem.communication.message.event.*;
import se.cygni.texasholdem.communication.message.request.ActionRequest;
import se.cygni.texasholdem.game.*;
import se.cygni.texasholdem.game.definitions.PlayState;
import se.cygni.texasholdem.game.definitions.PokerHand;
import se.cygni.texasholdem.game.definitions.Rank;
import se.cygni.texasholdem.game.util.PokerHandUtil;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * This is an example Poker bot player, you can use it as
 * a starting point when creating your own.
 * <p/>
 * If you choose to create your own class don't forget that
 * it must implement the interface Player
 *
 * @see Player
 * <p/>
 * Javadocs for common utilities and classes used may be
 * found here:
 * http://poker.cygni.se/mavensite/texas-holdem-common/apidocs/index.html
 * <p/>
 * You can inspect the games you bot has played here:
 * http://poker.cygni.se/showgame
 */
public class FullyImplementedBot implements Player {

    private static Logger log = LoggerFactory
            .getLogger(FullyImplementedBot.class);

    private final String serverHost;
    private final int serverPort;
    private final PlayerClient playerClient;
    private final HashMap[] startingHandsProp; //The number in the array symbolise the number of players around the table
    private final int NUMBER_OF_PLAYERS_IN_STATISTICS = 11; //Statistics only holds for tables between 2-10 players so slot 0,1 is overhead.
    private int raised;

    ClientEventDispatcher eventDispatcher = new ClientEventDispatcher(this);
    ClientEventDispatcher currentPlayStateDispatcher;
    CurrentPlayState currentPlayState;

    /**
     * Default constructor for a Java Poker Bot.
     *
     * @param serverHost IP or hostname to the poker server
     * @param serverPort port at which the poker server listens
     */
    public FullyImplementedBot(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        raised = 0;
        currentPlayState = new CurrentPlayState(getName());
        currentPlayStateDispatcher = new ClientEventDispatcher(currentPlayState.getPlayerImpl());

        //Load in starting hand statistics
        startingHandsProp = new HashMap[NUMBER_OF_PLAYERS_IN_STATISTICS];
        try {
            for (int i = 2; i < NUMBER_OF_PLAYERS_IN_STATISTICS; i++) {
                startingHandsProp[i] = new HashMap<String, Double>();
                Scanner scanner = new Scanner(new FileInputStream("src" + File.separator + "main" + File.separator + "resources" + File.separator + i + "players.stat"));
                while (scanner.hasNextLine()) {
                    String input = scanner.nextLine();
                    String[] keyValue = input.split("\\s");
                    startingHandsProp[i].put(keyValue[0], Float.valueOf(keyValue[1]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize the player client
        playerClient = new PlayerClient(this, serverHost, serverPort);
    }

    public CurrentPlayState getCurrentPlayState() {
        return currentPlayState;
    }

    public void dispatchEvent(TexasEvent event) {
        currentPlayStateDispatcher.onEvent(event);
        eventDispatcher.onEvent(event);
    }

    public void playATrainingGame() throws Exception {
        playerClient.connect();
        playerClient.registerForPlay(Room.TRAINING);
    }

    /**
     * The main method to start your bot.
     *
     * @param args
     */
    public static void main(String... args) {
        FullyImplementedBot bot = new FullyImplementedBot("poker.cygni.se", 4711); //poker.cygni.se

        try {
            bot.playATrainingGame();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * The name you choose must be unique, if another connected bot has
     * the same name your bot will be denied connection.
     *
     * @return The name under which this bot will be known
     */
    @Override
    public String getName() {
        return "Deep_Thought";
    }

    /**
     * This is where you supply your bot with your special mojo!
     * <p/>
     * The ActionRequest contains a list of all the possible actions
     * your bot can perform.
     *
     * @param request The list of Actions that the bot may perform.
     * @return The action the bot wants to perform.
     * @see ActionRequest
     * <p/>
     * Given the current situation you need to choose the best
     * action. It is not allowed to change any values in the
     * ActionRequest. The amount you may RAISE or CALL is already
     * predermined by the poker server.
     * <p/>
     * If an invalid Action is returned the server will ask two
     * more times. Failure to comply (i.e. returning an incorrect
     * or non valid Action) will result in a forced FOLD for the
     * current Game Round.
     * @see Action
     */
    @Override
    public Action actionRequired(ActionRequest request) {

        Action response = getBestAction(request);
        log.info("I'm going to {} {}",
                response.getActionType(),
                response.getAmount() > 0 ? "with " + response.getAmount() : "");

        return response;
    }

    /**
     * A helper method that returns this bots idea of the best action.
     * Note! This is just an example, you need to add your own smartness
     * to win.
     *
     * @param request
     * @return
     */
    private Action getBestAction(ActionRequest request) {


        Action callAction = null;
        Action checkAction = null;
        Action raiseAction = null;
        Action foldAction = null;
        Action allInAction = null;

        for (final Action action : request.getPossibleActions()) {
            switch (action.getActionType()) {
                case CALL:
                    callAction = action;
                    break;
                case CHECK:
                    checkAction = action;
                    break;
                case FOLD:
                    foldAction = action;
                    break;
                case RAISE:
                    raiseAction = action;
                    break;
                case ALL_IN:
                    allInAction = action;
                default:
                    break;
            }
        }

        // The current play state is accessible through this class. It
        // keeps track of basic events and other players.
        CurrentPlayState playState = playerClient.getCurrentPlayState();

        // The current BigBlind
        long currentBB = playState.getBigBlind();

        // PokerHandUtil is a hand classifier that returns the best hand given
        // the current community cards and your cards.
        PokerHandUtil pokerHandUtil = new PokerHandUtil(playState.getCommunityCards(), playState.getMyCards());
        Hand myBestHand = pokerHandUtil.getBestHand();
        PokerHand myBestPokerHand = myBestHand.getPokerHand();

        log.error(playState.getCurrentPlayState().toString());
        if (playState.getCurrentPlayState() == PlayState.PRE_FLOP) {
            return evaluatePreFlop(playState, callAction, checkAction, raiseAction, foldAction, allInAction);
        }

        if (playState.getCurrentPlayState() == PlayState.FLOP) {
            return evaluateFlop(playState, callAction, checkAction, raiseAction, foldAction, allInAction, myBestPokerHand);
        }

        if (playState.getCurrentPlayState() == PlayState.TURN) {
            return evaluateTurn(playState, callAction, checkAction, raiseAction, foldAction, allInAction, myBestPokerHand);
        }

        if (playState.getCurrentPlayState() == PlayState.RIVER) {
            return evaluateRiver(playState, callAction, checkAction, raiseAction, foldAction, allInAction, myBestPokerHand);
        }

        // failsafe
        return foldAction;
    }

    public Action evaluatePreFlop(CurrentPlayState playState, Action callAction, Action checkAction, Action raiseAction, Action foldAction, Action allInAction) {
        List<Card> cardsList = playState.getMyCards();
        String hand = Translator.translateFromShortString(cardsList);


        int potentialPlayers = playState.getNumberOfPlayers(); // - playState.getNumberOfFoldedPlayers();
        Float winProb = (Float) startingHandsProp[potentialPlayers].get(hand);
        //Better position if we are dealer
        if (playState.getDealerPlayer().equals(this)) {
            winProb += 5;
        }

        //If we have a good hand -> raise
        if (winProb > 60 && raiseAction != null) {
            raised = 0;
            return raiseAction;
        }

        if (getMyCardsTopTenRank(playState) > 0) {
            log.error("do i ever get here?");
            if(raiseAction != null ){
                return raiseAction;
            }
            if(callAction != null){
                return callAction;
            }
        }
        //Check if possible
        if (checkAction != null) {
            raised = 0;
            return checkAction;
        }

        if (winProb > 15 && callAction != null && (getCallAmount(callAction)<=100)) {
            return callAction;
        }

        if (winProb > 22 && callAction != null && (getCallAmount(callAction)<=300)) {
            return callAction;
        }
        if (winProb > 30 && callAction != null && (getCallAmount(callAction)<=1000)) {
            return callAction;
        }


        //failsafe
        raised = 0;
        log.error("folding with " + winProb + "%");
        return foldAction;
    }

    private Action evaluateFlop(CurrentPlayState playState, Action callAction, Action checkAction, Action raiseAction, Action foldAction, Action allInAction, PokerHand myBestPokerHand) {
        if (raiseAction != null && (
                (myBestPokerHand.getOrderValue() >= 3) ||
                        (getMyCardsTopTenRank(playState) > 5))) {
            return raiseAction;
        }

        // Otherwise, be more careful CHECK if possible.
        if (checkAction != null) {
            raised = 0;
            return checkAction;
        }

        // Only call if ONE_PAIR or better
        if (isHandBetterThan(myBestPokerHand, PokerHand.ONE_PAIR) && callAction != null) {
            raised = 0;
            return callAction;
        }

        // Do I have something better than TWO_PAIR and can RAISE?
        if (isHandBetterThan(myBestPokerHand, PokerHand.TWO_PAIRS) && raiseAction != null) {
            raised = 0;
            return raiseAction;
        }

        // I'm small blind and we're in PRE_FLOP, might just as well call
        if (playState.amISmallBlindPlayer() &&
                playState.getCurrentPlayState() == PlayState.PRE_FLOP &&
                callAction != null) {
            raised = 0;
            return callAction;
        }

        // failsafe
        return foldAction;
    }

    private Action evaluateTurn(CurrentPlayState playState, Action callAction, Action checkAction, Action raiseAction, Action foldAction, Action allInAction, PokerHand myBestPokerHand) {
        return evaluateFlop(playState,callAction,checkAction,raiseAction,foldAction,allInAction,myBestPokerHand);
    }

    private Action evaluateRiver(CurrentPlayState playState, Action callAction, Action checkAction, Action raiseAction, Action foldAction, Action allInAction, PokerHand myBestPokerHand) {
        return evaluateFlop(playState,callAction,checkAction,raiseAction,foldAction,allInAction,myBestPokerHand);
    }

    /**
     * Compares two pokerhands.
     *
     * @param myPokerHand
     * @param otherPokerHand
     * @return TRUE if myPokerHand is valued higher than otherPokerHand
     */
    private boolean isHandBetterThan(PokerHand myPokerHand, PokerHand otherPokerHand) {
        return myPokerHand.getOrderValue() > otherPokerHand.getOrderValue();
    }

    /**
     * @param callAction
     * @return the cost to call
     */
    private long getCallAmount(Action callAction) {
        return callAction == null ? -1 : callAction.getAmount();
    }

    /**
     * @param raiseAction
     * @return the amount that will be raised
     */
    private long getRaiseAmount(Action raiseAction) {
        return raiseAction == null ? -1 : raiseAction.getAmount();
    }

    private int getMyCardsTopTenRank(CurrentPlayState currentPlayState) {

        // 10: A - A
        if (doMyCardsContain(Rank.ACE, Rank.ACE, currentPlayState)) {
            return 10;
        }

        //  9: K - K
        if (doMyCardsContain(Rank.KING, Rank.KING, currentPlayState)) {
            return 9;
        }

        //  8: Q - Q
        if (doMyCardsContain(Rank.QUEEN, Rank.QUEEN, currentPlayState)) {
            return 8;
        }

        //  7: A - K
        if (doMyCardsContain(Rank.ACE, Rank.KING,currentPlayState)) {
            return 7;
        }

        //  6: J - J
        if (doMyCardsContain(Rank.JACK, Rank.JACK, currentPlayState)) {
            return 6;
        }

        //  5: 10 - 10
        if (doMyCardsContain(Rank.TEN, Rank.TEN, currentPlayState)) {
            return 5;
        }

        //  4: 9 - 9
        if (doMyCardsContain(Rank.NINE, Rank.NINE, currentPlayState)) {
            return 4;
        }

        //  3: 8 - 8
        if (doMyCardsContain(Rank.EIGHT, Rank.EIGHT, currentPlayState)) {
            return 3;
        }

        //  2: A - Q
        if (doMyCardsContain(Rank.ACE, Rank.QUEEN, currentPlayState)) {
            return 2;
        }

        //  1: 7 - 7
        if (doMyCardsContain(Rank.SEVEN, Rank.SEVEN, currentPlayState)) {
            return 1;
        }

        return 0;
    }

    private boolean doMyCardsContain(Rank rank1, Rank rank2, CurrentPlayState currentPlayState) {
        Card c1 = currentPlayState.getMyCards().get(0);
        Card c2 = currentPlayState.getMyCards().get(1);

        return (c1.getRank() == rank1 && c2.getRank() == rank2) ||
                (c1.getRank() == rank2 && c2.getRank() == rank1);
    }

    /**
     * **********************************************************************
     * <p/>
     * Event methods
     * <p/>
     * These methods tells the bot what is happening around the Poker Table.
     * The methods must be implemented but it is not mandatory to act on the
     * information provided.
     * <p/>
     * The helper class CurrentPlayState provides most of the book keeping
     * needed to keep track of the total picture around the table.
     *
     * @see CurrentPlayState
     * <p/>
     * ***********************************************************************
     */

    @Override
    public void onPlayIsStarted(final PlayIsStartedEvent event) {
        log.debug("Play is started");
    }

    @Override
    public void onTableChangedStateEvent(TableChangedStateEvent event) {

        log.debug("Table changed state: {}", event.getState());
    }

    @Override
    public void onYouHaveBeenDealtACard(final YouHaveBeenDealtACardEvent event) {

        log.debug("I, {}, got a card: {}", getName(), event.getCard());
    }

    @Override
    public void onCommunityHasBeenDealtACard(
            final CommunityHasBeenDealtACardEvent event) {

        log.debug("Community got a card: {}", event.getCard());
    }

    @Override
    public void onPlayerBetBigBlind(PlayerBetBigBlindEvent event) {

        log.debug("{} placed big blind with amount {}", event.getPlayer().getName(), event.getBigBlind());
    }

    @Override
    public void onPlayerBetSmallBlind(PlayerBetSmallBlindEvent event) {

        log.debug("{} placed small blind with amount {}", event.getPlayer().getName(), event.getSmallBlind());
    }

    @Override
    public void onPlayerFolded(final PlayerFoldedEvent event) {

        log.debug("{} folded after putting {} in the pot", event.getPlayer().getName(), event.getInvestmentInPot());
    }

    @Override
    public void onPlayerForcedFolded(PlayerForcedFoldedEvent event) {

        log.debug("NOT GOOD! {} was forced to fold after putting {} in the pot because exceeding the time limit", event.getPlayer().getName(), event.getInvestmentInPot());
    }

    @Override
    public void onPlayerCalled(final PlayerCalledEvent event) {

        log.debug("{} called with amount {}", event.getPlayer().getName(), event.getCallBet());
    }

    @Override
    public void onPlayerRaised(final PlayerRaisedEvent event) {
        raised++;
        log.debug("{} raised with bet {}", event.getPlayer().getName(), event.getRaiseBet());
    }

    @Override
    public void onTableIsDone(TableIsDoneEvent event) {

        log.debug("Table is done, I'm leaving the table with ${}", playerClient.getCurrentPlayState().getMyCurrentChipAmount());
        log.info("Ending poker session, the last game may be viewed at: http://{}/showgame/table/{}", serverHost, playerClient.getCurrentPlayState().getTableId());
    }

    @Override
    public void onPlayerWentAllIn(final PlayerWentAllInEvent event) {

        log.debug("{} went all in with amount {}", event.getPlayer().getName(), event.getAllInAmount());
    }

    @Override
    public void onPlayerChecked(final PlayerCheckedEvent event) {

        log.debug("{} checked", event.getPlayer().getName());
    }

    @Override
    public void onYouWonAmount(final YouWonAmountEvent event) {

        log.debug("I, {}, won: {}", getName(), event.getWonAmount());
    }

    @Override
    public void onShowDown(final ShowDownEvent event) {

        if (!log.isInfoEnabled()) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        final Formatter formatter = new Formatter(sb);

        sb.append("ShowDown:\n");

        for (final PlayerShowDown psd : event.getPlayersShowDown()) {
            formatter.format("%-13s won: %6s  hand: %-15s ",
                    psd.getPlayer().getName(),
                    psd.getHand().isFolded() ? "Fold" : psd.getWonAmount(),
                    psd.getHand().getPokerHand().getName());

            sb.append(" cards: | ");
            for (final Card card : psd.getHand().getCards()) {
                formatter.format("%-13s | ", card);
            }
            sb.append("\n");
        }

        log.info(sb.toString());
    }

    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {

        log.debug("Player {} has quit", event.getPlayer());
    }

    @Override
    public void connectionToGameServerLost() {
        log.debug("Lost connection to game server, exiting");
        System.exit(0);
    }

    @Override
    public void connectionToGameServerEstablished() {

        log.debug("Connection to game server established");
    }

    @Override
    public void serverIsShuttingDown(final ServerIsShuttingDownEvent event) {
        log.debug("Server is shutting down");
    }


}
