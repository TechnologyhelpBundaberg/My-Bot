/*
 * Copyright 2018 John Grosh (john.a.grosh@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.vortex.commands.tools;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.commons.utils.FinderUtil;
import com.jagrosh.vortex.Action;
import com.jagrosh.vortex.Constants;
import com.jagrosh.vortex.commands.CommandExceptionListener.CommandErrorException;
import com.jagrosh.vortex.commands.CommandExceptionListener.CommandWarningException;
import com.jagrosh.vortex.utils.FormatUtil;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audit.ActionType;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.restaction.pagination.AuditLogPaginationAction;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class AuditCmd extends Command
{
    private final String actions;
    private final String linestart = "\u25AB";
    private final String unknown = "*Unknown*";
    
    public AuditCmd()
    {
        this.name = "audit";
        this.aliases = new String[]{"auditlog","auditlogs","audits"};
        this.arguments = "<ALL | FROM | ACTION> [target]";
        this.help = "fetches recent audit logs";
        this.guildOnly = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS, Permission.VIEW_AUDIT_LOGS, Permission.MESSAGE_EXT_EMOJI};
        this.userPermissions = new Permission[]{Permission.VIEW_AUDIT_LOGS};
        this.cooldown = 5;
        this.category = new Category("Tools");
        StringBuilder sb = new StringBuilder("\n\nValid actions: `");
        for(ActionType type: ActionType.values())
            sb.append(type.name()).append("`, `");
        actions = sb.toString().substring(0, sb.length()-3);
    }
    
    @Override
    protected void execute(CommandEvent event)
    {
        String[] parts = event.getArgs().split("\\s+", 2);
        AuditLogPaginationAction action = event.getGuild().getAuditLogs().cache(false).limit(10); 
        switch(parts[0].toLowerCase())
        {
            case "all":
                break;
            case "from":
                if(parts.length==1)
                    throw new CommandErrorException("Please include a user");
                List<User> list = FinderUtil.findUsers(parts[1], event.getJDA());
                if(list.isEmpty())
                    throw new CommandWarningException("No users found matching `"+parts[1]+"`");
                if(list.size()>1)
                    throw new CommandWarningException(FormatUtil.listOfUser(list, event.getArgs()));
                action = action.user(list.get(0));
                break;
            case "action":
                if(parts.length==1)
                    throw new CommandErrorException("Please include an action"+actions);
                ActionType type = null;
                for(ActionType t: ActionType.values())
                {
                    if(t.name().toLowerCase().replace("_", "").equals(parts[1].toLowerCase().replace("_", "").replace(" ", "")))
                    {
                        type = t;
                        break;
                    }
                }
                if(type==null)
                    throw new CommandErrorException("Please include a valid action"+actions);
                action = action.type(type);
                break;
            default:
                throw new CommandErrorException("Valid subcommands:\n\n"
                    + "`" + Constants.PREFIX + name + " all` - shows recent audit log entries\n"
                    + "`" + Constants.PREFIX + name + " from <user>` - shows recent entries by a user\n"
                    + "`" + Constants.PREFIX + name + " action <action>` shows recent entries of a certain action");
        }
        event.getChannel().sendTyping().queue();
        action.queue(list -> 
        {
            if(list.isEmpty())
            {
                event.replyWarning("No audit log entries found matching your criteria");
                return;
            }
            EmbedBuilder eb = new EmbedBuilder().setColor(event.getSelfMember().getColor());
            list.forEach(ale -> 
            {
                StringBuilder sb = new StringBuilder();
                sb.append(linestart).append("User: ").append(FormatUtil.formatFullUser(ale.getUser()));
                switch(ale.getTargetType())
                {
                    case CHANNEL: 
                        TextChannel tc = event.getGuild().getTextChannelById(ale.getTargetIdLong());
                        sb.append("\n").append(linestart).append("Channel: ")
                                .append(tc==null ? unknown : "**#"+tc.getName()+"**")
                                .append(" (ID:").append(ale.getTargetId()).append(")"); 
                        break;
                    case EMOTE:
                        Emote e = event.getGuild().getEmoteById(ale.getTargetIdLong());
                        sb.append("\n").append(linestart).append("Emote: ")
                                .append(e==null ? unknown : e.getAsMention())
                                .append(" (ID:").append(ale.getTargetId()).append(")");
                        break;
                    case GUILD:
                        break;
                    case INVITE:
                        break;
                    case MEMBER:
                        User u = event.getJDA().getUserById(ale.getTargetIdLong());
                        sb.append("\n").append(linestart).append("Member: ")
                                .append(u==null ? unknown : FormatUtil.formatFullUser(u));
                        break;
                    case ROLE:
                        Role r = event.getGuild().getRoleById(ale.getTargetIdLong());
                        sb.append("\n").append(linestart).append("Role: ")
                                .append(r==null ? unknown : "**"+r.getName()+"**")
                                .append(" (ID:").append(ale.getTargetId()).append(")");
                        break;
                    case WEBHOOK:
                        sb.append("\n").append(linestart).append("Webhook ID: ").append(ale.getTargetId());
                        break;
                    case UNKNOWN:
                    default:
                        sb.append("\n").append(linestart).append("Target ID: ").append(ale.getTargetId());
                }
                ale.getChanges().keySet().forEach(change -> 
                {
                    sb.append("\n").append(linestart).append(fixCase(change)).append(": ")
                            .append(ale.getChangeByKey(change).getOldValue()==null ? "" : "**"+ale.getChangeByKey(change).getOldValue()+"**")
                            .append(ale.getChangeByKey(change).getOldValue()==null || ale.getChangeByKey(change).getNewValue()==null ? "" : " → ")
                            .append(ale.getChangeByKey(change).getNewValue()==null ? "" : "**"+ale.getChangeByKey(change).getNewValue()+"**");
                });
                ale.getOptions().keySet().forEach(option -> 
                {
                    sb.append("\n").append(linestart).append(fixCase(option)).append(": ")
                            .append(ale.getOptionByName(option)==null ? unknown : "**"+ale.getOptionByName(option)+"**");
                });
                if(ale.getReason()!=null)
                    sb.append("\n").append(linestart).append("Reason: ").append(ale.getReason());
                sb.append("\n").append(linestart).append("Time: **").append(ale.getCreationTime().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("**\n\u200B");
                String str = sb.length()>1024 ? sb.substring(0,1020)+" ..." : sb.toString();
                eb.addField(actionToEmote(ale.getType())+" "+fixCase(ale.getType().name()), str, true);
            });
            event.reply(new MessageBuilder()
                    .setContent(Constants.SUCCESS+" Recent Audit Logs in **"+FormatUtil.filterEveryone(event.getGuild().getName())+"**:")
                    .setEmbed(eb.build()).build());
        }, f -> event.replyWarning("Failed to retrieve audit logs"));
    }
    
    private String fixCase(String input)
    {
        String ret = "";
        for(int i=0; i<input.length(); i++)
            if(input.charAt(i) == '_')
                ret += " ";
            else if(i==0 || input.charAt(i-1) == '_')
                ret += Character.toUpperCase(input.charAt(i));
            else 
                ret += Character.toLowerCase(input.charAt(i));
        return ret;
    }
    
    private String actionToEmote(ActionType type)
    {
        switch(type)
        {
            case BAN:                     return Action.BAN.getEmoji();
            case KICK:                    return Action.KICK.getEmoji();
            case UNBAN:                   return Action.UNBAN.getEmoji();
            case PRUNE:                   return "\u2702";
            
            case CHANNEL_CREATE:          return "\uD83D\uDCFA";
            case CHANNEL_DELETE:          return "\uD83D\uDCFA";
            case CHANNEL_UPDATE:          return "\uD83D\uDCFA";
            
            case CHANNEL_OVERRIDE_CREATE: return "\uD83D\uDCFA";
            case CHANNEL_OVERRIDE_DELETE: return "\uD83D\uDCFA";
            case CHANNEL_OVERRIDE_UPDATE: return "\uD83D\uDCFA";
            
            case EMOTE_CREATE:            return "\uD83D\uDD23";
            case EMOTE_DELETE:            return "\uD83D\uDD23";
            case EMOTE_UPDATE:            return "\uD83D\uDD23";
            
            case GUILD_UPDATE:            return "\uD83D\uDDA5";
            
            case INVITE_CREATE:           return "\uD83D\uDCE5";
            case INVITE_DELETE:           return "\uD83D\uDCE5";
            case INVITE_UPDATE:           return "\uD83D\uDCE5";
            
            case MEMBER_ROLE_UPDATE:      return "\uD83C\uDFAD";
            case MEMBER_UPDATE:           return "\u270D";
            
            case MESSAGE_CREATE:          return "\u2705";
            case MESSAGE_DELETE:          return "\u274C";
            case MESSAGE_UPDATE:          return "\u26A0";
            
            case ROLE_CREATE:             return "\uD83D\uDEA6";
            case ROLE_DELETE:             return "\uD83D\uDEA6";
            case ROLE_UPDATE:             return "\uD83D\uDEA6";
            
            case WEBHOOK_CREATE:          return "\uD83C\uDF10";
            case WEBHOOK_REMOVE:          return "\uD83C\uDF10";
            case WEBHOOK_UPDATE:          return "\uD83C\uDF10";
            
            case UNKNOWN:                 return "\u2753";
            default:                      return "\u2753";
        }
    }
}
