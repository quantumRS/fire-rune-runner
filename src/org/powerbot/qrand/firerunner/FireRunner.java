/**
 * Created by qrand on 2/16/16.
 * Copyright GNU GPL v3.0
 * All copies and derivations must attribute original creator and carry this same license.
 */

package org.powerbot.qrand.firerunner;

import org.powerbot.script.*;
import org.powerbot.script.rt6.*;
import org.powerbot.script.rt6.ClientContext;

import java.awt.*;
import java.util.concurrent.Callable;


@Script.Manifest(name = "Fire Rune Runner",
        description = "Works with Rune Essence and Pure Essence. Start near Fire Alter portal.")
public class FireRunner extends PollingScript<ClientContext> implements MessageListener, PaintListener{

    private static final int FIRE_RUNE = 554, ANAGOGIC_ORT = 24909,
    /*RUNE_ESSENCE = 1436,*/ RUINS = 2456, ALTER = 2482, PORTAL = 2469;

    private static final int[] ESSENCE = {1436, 7936};

    //tiles to walk to
    public static final Tile RUINS_TILE = new Tile(3313, 3253, 0);
    public static final Tile ALTER_TILE = new Tile(2584, 4840, 0);
    public static final Tile PORTAL_TILE = new Tile(2577, 4846, 0);
    public static final Tile BANK_TILE = new Tile(3348, 3238, 0);

    public static final Area ALTER_AREA = new Area(new Tile(2569, 4855, 0), new Tile(2596, 4825, 0));

    private int runes = 0, runesPerHour = 0;
    private long startTime = System.currentTimeMillis(), last = 0;

    private String debugMsg = "";

    private enum State {
        BANK,
        MAKE_RUNES,
        LEAVE_RUINS,
        ENTER_RUINS,
        PICKUP_ORT
    }

    public void poll() {

        //debugMsg = ""+Random.nextGaussian(0, 45000, 3000, 1000);

        final State state = getState();
        if (state == null) {
            return;
        }
        if (ctx.players.local().animation() != -1) {
            last = System.currentTimeMillis();
        }

        //Prevents spam clicking
        if (System.currentTimeMillis() - last < 1500) {
            return;
        }
        switch (state) {
            case BANK:
                if (!ctx.bank.inViewport() || BANK_TILE.distanceTo(ctx.players.local().tile()) > 10) {
                    ctx.movement.step(BANK_TILE);
                    ctx.camera.turnTo(BANK_TILE);

                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return (!ctx.players.local().inMotion()
                                    || ctx.movement.destination().distanceTo(ctx.players.local()) < 5);
                        }
                    }, 250, 15);

                } else if (ctx.bank.open()) {

                    if (!ctx.backpack.select().id(FIRE_RUNE).isEmpty()){
                        Condition.sleep(Random.getDelay());
                        ctx.bank.depositInventory();
                        Condition.sleep(Random.getDelay());
                    }

                    if (ctx.bank.select().id(ESSENCE).isEmpty()) {
                        ctx.bank.close();
                        ctx.controller.stop();
                    } else if (ctx.bank.withdraw(ESSENCE[1], Bank.Amount.ALL)) {
                        Condition.sleep(Random.getDelay());
                        ctx.bank.close();
                        Condition.wait(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                return !ctx.bank.opened();
                            }
                        }, 250, 10);
                    } else if (ctx.bank.withdraw(ESSENCE[0], Bank.Amount.ALL)) {
                        Condition.sleep(Random.getDelay());
                        ctx.bank.close();
                        Condition.wait(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                return !ctx.bank.opened();
                            }
                        }, 250, 10);
                    }
                }
                break;
            case MAKE_RUNES:
                if (!ctx.objects.select().id(ALTER).nearest().poll().inViewport()) {
                    ctx.movement.step(ALTER_TILE);
                    ctx.camera.turnTo(ALTER_TILE);
                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return (ctx.players.local().inMotion()
                                    || ctx.movement.destination().distanceTo(ctx.players.local()) < 5);
                        }
                    }, 250, 15);
                }
                else {
                    ctx.objects.select().id(ALTER).nearest().poll().click();
                }
                break;
            case LEAVE_RUINS:
                if (!ctx.objects.select().id(PORTAL).nearest().poll().inViewport()) {
                    //ctx.movement.step(PORTAL_TILE);
                    ctx.camera.turnTo(PORTAL_TILE);
                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return (!ctx.players.local().inMotion()
                                    || ctx.movement.destination().distanceTo(ctx.players.local()) < 5);
                        }
                    }, 250, 15);
                }
                else  if(!ctx.players.local().inMotion()){
                    ctx.objects.select().id(PORTAL).nearest().poll().click();
                }
                break;
            case ENTER_RUINS:
                if (!ctx.objects.select().id(RUINS).nearest().poll().inViewport()) {
                    ctx.movement.step(RUINS_TILE);
                    ctx.camera.turnTo(RUINS_TILE);

                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return (!ctx.players.local().inMotion()
                                    || ctx.movement.destination().distanceTo(ctx.players.local()) < 5);
                        }
                    }, 250, 15);
                }
                else {
                    if (!ctx.players.local().inMotion()
                            || ctx.movement.destination().distanceTo(ctx.players.local()) < 5){
                        ctx.objects.select().id(RUINS).nearest().poll().click();
                    }
                }
                break;
            case PICKUP_ORT:
                ctx.camera.turnTo(ctx.groundItems.select().id(ANAGOGIC_ORT).nearest().poll().tile());
                ctx.groundItems.select().id(ANAGOGIC_ORT).poll().click();
                break;

        }
    }

    private State getState() {

        if (!ctx.groundItems.select().id(ANAGOGIC_ORT).isEmpty()){
            return State.PICKUP_ORT;
        }

        if (!ctx.backpack.select().id(ESSENCE).isEmpty()){
            return (ALTER_AREA.contains(ctx.players.local().tile()))
                    ? State.MAKE_RUNES : State.ENTER_RUINS;
        }
        else {
            return (ALTER_AREA.contains(ctx.players.local().tile()))
                    ? State.LEAVE_RUINS : State.BANK;
        }
    }

    @Override
    public void messaged(MessageEvent e) {
        final String msg = e.text().toLowerCase();
        if (e.source().isEmpty() && msg.contains("you bind the temple's power into fire runes.")) {
            runes += ctx.backpack.select().id(FIRE_RUNE).count(true);
            runesPerHour = (int)((float)runes/((float)(System.currentTimeMillis() - startTime) / 3600000.0));
        }
    }

    @Override
    public void repaint(Graphics g){
        Point cursor = ctx.input.getLocation();
        Graphics2D g2 = (Graphics2D)g;
        int x = (int)cursor.getX(), y = (int)cursor.getY();

        g2.drawLine(x,y-10,x,y+10);
        g2.drawLine(x-10,y,x+10,y);
        g2.setColor(Color.WHITE);
        g2.drawString("State: " + getState().toString(), 16, 16); // Displays last message logged
        g2.drawString("Runes Crafted (per hour): "+runes+" ("+runesPerHour+")", 16, 32);
        //g2.drawString("Debug: "+debugMsg, 16, 48);
    }



}
