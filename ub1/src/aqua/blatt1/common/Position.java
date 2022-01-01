package aqua.blatt1.common;

public enum Position {
    LEFT(-1), RIGHT(+1), HERE(0);

    private int position;

    private Position(int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
