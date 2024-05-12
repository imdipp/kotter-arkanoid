import com.varabyte.kotter.foundation.anim.Anim
import com.varabyte.kotter.foundation.anim.renderAnimOf
import com.varabyte.kotter.foundation.firstSuccess
import com.varabyte.kotter.foundation.input.*
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.*
import com.varabyte.kotter.foundation.timer.addTimer
import com.varabyte.kotter.runtime.render.*
import com.varabyte.kotter.terminal.system.SystemTerminal
import com.varabyte.kotter.terminal.virtual.TerminalSize
import com.varabyte.kotter.terminal.virtual.VirtualTerminal
import com.varabyte.kotterx.text.shiftRight
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

val title =
    """
     ______  ______  __  __  ______  __   __  ______  __  _____    
    /\  __ \/\  == \/\ \/ / /\  __ \/\ "-.\ \/\  __ \/\ \/\  __-.  
    \ \  __ \ \  __<\ \  _"-\ \  __ \ \ \-.  \ \ \/\ \ \ \ \ \/\ \ 
     \ \_\ \_\ \_\ \_\ \_\ \_\ \_\ \_\ \_\\"\_\ \_____\ \_\ \____- 
      \/_/\/_/\/_/ /_/\/_/\/_/\/_/\/_/\/_/ \/_/\/_____/\/_/\/____/        
                                                  
    """.trimIndent().split("\n")


fun RenderScope.boundaries() = white(ColorLayer.BG) { text(' ') }
fun RenderScope.floor() = black(ColorLayer.BG) { text(' ') }
fun RenderScope.bricksYellow() = yellow(ColorLayer.BG) { text(' ') }
fun RenderScope.player() = green(ColorLayer.BG){ text(' ') }
fun RenderScope.playerLeftRacket() = green(ColorLayer.BG){ text(' ') }
fun RenderScope.playerRightRacket() = green(ColorLayer.BG){ text(' ') }
fun RenderScope.ball() = red { text('o') }


private const val WIDTH = 40
private const val HEIGHT = 25
private val PLAYER_SPAWN_POINT = Point(row = ( HEIGHT / 2 ) + 10, col =  WIDTH / 2)
private var BRICKS_ROWS = 10
private var BALL_SPEED = 100L
private var PLAYER_NAME = "DIP"

enum class Dir { IDLE, LEFT, RIGHT, DIUPRIGHT, DIUPLEFT, DIDOWNRIGHT, DIDOWNLEFT, UP, DOWN }

data class Point(val col: Int, val row: Int)

operator fun Point.plus(dir: Dir): Point{
    return when (dir) {
        Dir.IDLE -> Point(col, row)
        Dir.LEFT -> Point(col - 1, row)
        Dir.RIGHT -> Point(col + 1, row)
        Dir.DIUPRIGHT -> Point(col + 1, row - 1)
        Dir.DIUPLEFT -> Point(col - 1, row - 1)
        Dir.DIDOWNRIGHT -> Point(col - 1, row + 1)
        Dir.DIDOWNLEFT -> Point(col + 1, row + 1)
        Dir.UP -> Point(col, row - 1)
        Dir.DOWN -> Point(col, row + 1)
    }
}

class Ball(val level: Level, var point: Point, var player: Player){

    fun isBall(pt:Point): Boolean = pt == point

    var isReleased: Boolean = false

    var onMoved: () -> Unit = {}

    var currDir = Dir.IDLE

    fun move(dir: Dir = currDir){
        currDir = dir
        var nextPoint = this.point.plus(dir)
        if(!isReleased){
            if (nextPoint.col == WIDTH - 2 || nextPoint.col == 2) {
                return
            }else{
                nextPoint = this.point.plus(currDir)
            }
        }
        if(level.isBricks(nextPoint)){
            level.removeBrick(nextPoint)
            when{
                currDir == Dir.UP -> {
                    currDir = Dir.DOWN
                    nextPoint = this.point.plus(currDir)
                }
                currDir == Dir.DIUPLEFT -> {
                    currDir = Dir.DIDOWNRIGHT
                    nextPoint = this.point.plus(currDir)
                }
                currDir == Dir.DIUPRIGHT -> {
                    currDir = Dir.DIDOWNLEFT
                    nextPoint = this.point.plus(currDir)
                }
            }
        }
        if(level.isBoundary(nextPoint)){
            when (currDir) {
                Dir.DIUPLEFT -> {
                    currDir = Dir.DIUPRIGHT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIUPRIGHT -> {
                    currDir = Dir.DIUPLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNRIGHT -> {
                    currDir = Dir.DIDOWNLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNLEFT -> {
                    currDir = Dir.DIDOWNRIGHT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.UP -> {
                    currDir = Dir.DOWN
                    nextPoint = this.point.plus(currDir)
                }

                else -> {}
            }
        }
        if(level.isDeadZone(nextPoint)){
            player.onDied()
        }
        if(player.isPlayer(nextPoint)){
            when (currDir) {
                Dir.DOWN -> {
                    currDir = Dir.UP
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNRIGHT -> {
                    currDir = Dir.DIUPLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNLEFT -> {
                    currDir = Dir.DIUPRIGHT
                    nextPoint = this.point.plus(currDir)
                }

                else -> {}
            }
        }
        if(player.isPlayerLeftRacket(nextPoint)){
            when (currDir) {
                Dir.DOWN -> {
                    currDir = Dir.DIUPLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNRIGHT -> {
                    currDir = Dir.DIUPLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNLEFT -> {
                    currDir = Dir.DIUPRIGHT
                    nextPoint = this.point.plus(currDir)
                }
                else -> {}
            }
        }
        if(player.isPlayerRightRacket(nextPoint)){
            when (currDir) {
                Dir.DOWN -> {
                    currDir = Dir.DIUPRIGHT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNRIGHT -> {
                    currDir = Dir.DIUPLEFT
                    nextPoint = this.point.plus(currDir)
                }
                Dir.DIDOWNLEFT -> {
                    currDir = Dir.DIUPRIGHT
                    nextPoint = this.point.plus(currDir)
                }

                else -> {}
            }
        }
        this.point = nextPoint
        onMoved()
    }

    fun release(dir: Dir = Dir.UP){
        isReleased = true
        move(dir)
    }
}

class Player(val level: Level, var point: Point, var pointL: Point, var pointR: Point){
    fun isPlayer(pt: Point): Boolean = pt == point

    fun isPlayerLeftRacket(pt: Point): Boolean = pt == pointL
    fun isPlayerRightRacket(pt: Point): Boolean = pt == pointR

    var onMoved: () -> Unit = {}
    var onDied: () -> Unit = {}

    fun move(dir: Dir = Dir.IDLE){
        val nextPoint = this.point.plus(dir)
        val nextPointL = this.pointL.plus(dir)
        val nextPointR = this.pointR.plus(dir)
        if (!level.isBoundary(Point(nextPointL.col, nextPointL.row)) &&
            !level.isBoundary(Point(nextPointR.col, nextPointR.row)) ) {
            this.point = nextPoint
            this.pointL = nextPointL
            this.pointR = nextPointR
            onMoved()
        }
    }
}

class Level {
    var score: Int = 0
    val bricks: MutableMap<Point, Char> = mutableMapOf()
    val player = Player(this, point = PLAYER_SPAWN_POINT, pointL = PLAYER_SPAWN_POINT.plus(Dir.LEFT), pointR = PLAYER_SPAWN_POINT.plus(Dir.RIGHT))
    val ball = Ball(this, point = Point(PLAYER_SPAWN_POINT.col, PLAYER_SPAWN_POINT.row - 1), player)
    init {
        for(i in 1..BRICKS_ROWS) {
            for (x in 0..WIDTH - 1 ) {
                bricks[Point(col = x, row = i)] = ' '
            }
        }
    }
    fun isDeadZone(pt: Point): Boolean =
        pt.row == HEIGHT

    fun isBoundary(pt: Point): Boolean =
        pt.col == WIDTH || pt.row == HEIGHT || pt.col == 0 || pt.row == 0

    fun isBricks(pt: Point): Boolean =
        bricks.contains(pt)

    var onRemoveBrick: () -> Unit = {}
    var onWin: () -> Unit = {}
    fun removeBrick(pt: Point){
        bricks.remove(pt)
        score ++
        onRemoveBrick()
        if(bricks.size == 0){
            onWin()
        }
    }
}

fun main() = session(
    terminal = listOf(
        { SystemTerminal() },
        { VirtualTerminal.create(terminalSize = TerminalSize(WIDTH + 35, HEIGHT - 15)) }
    ).firstSuccess(),
    clearTerminal = true
)  {
    Thread.sleep(500)
    val scrollUpAnimation =
        renderAnimOf(title.size, 120.milliseconds, looping = false) { frameIndex ->
            for (i in 0 until (title.size - frameIndex - 1)){
                textLine()
            }
            for (i in 0..frameIndex){
                textLine(title[i])
            }
        }
    section {
        shiftRight(5) { green { scrollUpAnimation(this) }}
        shiftRight(27) { green { textLine("PRESS SPACE TO START") } }
        shiftRight(27) { green { textLine("PRESS   Q   TO EXIT") } }
    }.runUntilKeyPressed(Keys.Q) {
        onKeyPressed {
            when (key) {
                Keys.SPACE -> renderGame()
            }
        }
    }
}

fun renderGame() = session(
    terminal = listOf(
        { SystemTerminal() },
        { VirtualTerminal.create(terminalSize = TerminalSize(WIDTH + 35, HEIGHT + 9)) }
    ).firstSuccess(),
    clearTerminal = true
) {
    var level = Level()
    var isDead by liveVarOf(false)
    var won = false
    section {
        shiftRight(15) {
            textLine()
            textLine("Press SPACE to release the ball.")
            textLine("Press ARROW KEYS to move.")
            textLine("Press Q to quit.")
            textLine()
            textLine()
        }
    }.run()
    section {
        shiftRight(15) {
            for (y in 0..HEIGHT) {
                for (x in 0..WIDTH) {
                    val point = Point(x, y)
                    when {
                        level.isBoundary(point) -> boundaries()
                        level.isBricks(point) -> bricksYellow()
                        level.player.isPlayer(point) -> player()
                        level.player.isPlayerLeftRacket(point) -> playerLeftRacket()
                        level.player.isPlayerRightRacket(point) -> playerRightRacket()
                        level.ball.isBall(point) -> ball()
                        else -> floor()
                    }
                }
                textLine()
            }
            textLine()
            yellow { textLine("SCORE: ${level.score}") }
            if (!isDead) {
                textLine()
            } else {
                red { textLine("Game Over! Press R to restart.") }
            }
            if (!won) {
                textLine()
            } else {
                green { textLine("You win! Press R to restart.") }
            }
        }
    }.runUntilKeyPressed(Keys.Q) {
        fun initGame(level : Level) {
            var currTickMs = 0L
            var moveTickMs = BALL_SPEED
            val player = level.player
            val ball = level.ball
            player.onMoved = {
                rerender()
            }
            level.onRemoveBrick = {
                rerender()
            }
            level.onWin = {
                won = true
            }
            player.onDied = {
                isDead = true
            }
            ball.onMoved = {
                if(ball.isReleased){
                    addTimer(Anim.ONE_FRAME_60FPS, repeat = true, key = ball) {
                        currTickMs += elapsed.inWholeMilliseconds
                        if (currTickMs >= moveTickMs) {
                            ball.move() // As a side effect, will reset currTickMs
                        }
                        repeat = !isDead
                    }
                    currTickMs = 0
                    rerender()
                }
            }
        }
        initGame(level)
        onKeyPressed {
            if (!isDead && !won){
                when (key) {
                    Keys.LEFT -> {
                        if(!level.ball.isReleased){
                            level.ball.move(Dir.LEFT)
                        }
                        level.player.move(Dir.LEFT)
                    }
                    Keys.RIGHT -> {
                        if(!level.ball.isReleased){
                            level.ball.move(Dir.RIGHT)
                        }
                        level.player.move(Dir.RIGHT)
                    }
                    Keys.SPACE -> level.ball.release()
                }
            } else {
                if (key == Keys.R){
                    level = Level()
                    isDead = false
                    level.score = 0
                    initGame(level)
                    rerender()
                }
            }
        }
    }
}