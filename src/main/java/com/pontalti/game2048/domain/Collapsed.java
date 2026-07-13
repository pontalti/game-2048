package com.pontalti.game2048.domain;

/** A collapsed grid together with the points its merges earned. */
public record Collapsed(Integer[][] grid, int points) {

}
