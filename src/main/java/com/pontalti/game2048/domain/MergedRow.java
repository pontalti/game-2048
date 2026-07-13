package com.pontalti.game2048.domain;

/** A merged row together with the points its merges earned. */
public record MergedRow(Integer[] row, int points) {

}