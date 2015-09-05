package com.bol.game

import spock.lang.Specification
import spock.lang.Unroll

class GameSpec extends Specification {

    def "make a turn in the beginning of a game"() {
        given:
        def game = new Game()

        when:
        game.pick(pit)

        then:
        game.players[0] == outcome as int[]
        game.players[1] == pits2 as int[]

        where:
        pit | pits2                 || outcome
        0   | [6, 6, 6, 6, 6, 6, 0] || [0, 7, 7, 7, 7, 7, 1]
        1   | [6, 6, 6, 6, 6, 6, 0] || [7, 0, 7, 7, 7, 7, 1]
        2   | [6, 6, 6, 6, 6, 6, 0] || [7, 7, 0, 7, 7, 7, 1]
        3   | [6, 6, 6, 6, 6, 6, 0] || [7, 7, 7, 0, 7, 7, 1]
        4   | [6, 6, 6, 6, 6, 6, 0] || [7, 7, 7, 7, 0, 7, 1]
        5   | [6, 6, 6, 6, 6, 6, 0] || [7, 7, 7, 7, 7, 0, 1]
    }

    def "multiple rounds"() {
        given:
        def game = new Game()

        when:
        picks.each { pit ->
            game.pick(pit)
        }

        then:
        game.players[0] == pits1 as int[]
        game.players[1] == pits2 as int[]

        where:
        picks = [0, 1, 2, 3, 4, 5]
        pits1 = [4, 2, 10, 2, 1, 1, 12]
        pits2 = [8, 1, 1, 8, 0, 8, 14]
    }

    @Unroll
    def "game over for #players"() {
        given:
        def game = new Game(players as int[][])

        when:
        def isOver = game.pick(pit)

        then:
        isOver == over

        where:
        players                                         | pit || over
        [[0, 0, 0, 0, 0, 1, 10], [6, 5, 4, 3, 2, 1, 5]] | 5   || true
        [[0, 0, 0, 0, 0, 2, 10], [6, 0, 0, 0, 0, 0, 5]] | 5   || true
        [[1, 0, 0, 0, 0, 2, 10], [6, 5, 4, 3, 2, 1, 5]] | 5   || false
    }
}