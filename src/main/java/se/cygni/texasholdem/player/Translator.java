package se.cygni.texasholdem.player;

import se.cygni.texasholdem.game.Card;
import se.cygni.texasholdem.game.definitions.Rank;

import java.util.LinkedList;
import java.util.List;

/**
 * A class to convert two cards into the way the statistics is based
 *
 *
 * Created by Mattias-0736491529 on 2014-03-08.
 */
class Translator {

    public static String translateFromShortString(List<Card> cards){

        Card first = cards.get(0);
        Card second = cards.get(1);

        Rank firstRank = first.getRank();
        Rank secondRank = second.getRank();

        StringBuilder sb = new StringBuilder();

        if(firstRank.compareTo(secondRank) > 0){
            sb.append(secondRank.getName());
            sb.append(firstRank.getName());
        } else {
            sb.append(firstRank.getName());
            sb.append(secondRank.getName());
        }

        if(first.getSuit().equals(second.getSuit())){
            sb.append("s");
        }

        return sb.toString();
    }

}
