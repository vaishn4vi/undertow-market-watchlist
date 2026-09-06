package com.undertow.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.common.exception.InvalidRequestException;
import com.undertow.common.exception.NotFoundException;
import com.undertow.watchlist.dto.AddWatchlistItemRequest;
import com.undertow.watchlist.dto.CreateWatchlistRequest;
import com.undertow.watchlist.dto.UpdateWatchlistItemRequest;
import com.undertow.watchlist.dto.UpdateWatchlistRequest;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.entity.WatchlistItem;
import com.undertow.watchlist.service.WatchlistService;

@SpringBootTest
@ActiveProfiles("test")
class WatchlistServiceTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void createAndListReturnsCreatedWatchlist() {
        Watchlist created = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Tech"));

        List<Watchlist> watchlists = watchlistService.listWatchlists(USER_A);

        assertThat(watchlists).extracting(Watchlist::getId).contains(created.getId());
        assertThat(created.getName()).isEqualTo("Tech");
    }

    @Test
    void newWatchlistHasNoItems() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Empty One"));

        List<WatchlistItem> items = watchlistService.listItems(USER_A, watchlist.getId());

        assertThat(items).isEmpty();
    }

    @Test
    void renameWatchlistUpdatesName() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Old Name"));

        Watchlist updated = watchlistService.updateWatchlist(
                USER_A, watchlist.getId(), new UpdateWatchlistRequest("New Name", null));

        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    void deletingWatchlistAlsoRemovesItsItems() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Doomed"));
        watchlistService.addItem(USER_A, watchlist.getId(), new AddWatchlistItemRequest("BHRT"));

        watchlistService.deleteWatchlist(USER_A, watchlist.getId());

        assertThatThrownBy(() -> watchlistService.listItems(USER_A, watchlist.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addingSameSymbolTwiceIsIdempotentNotAnError() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Dupe Test"));

        WatchlistItem first = watchlistService.addItem(USER_A, watchlist.getId(), new AddWatchlistItemRequest("GNGS"));
        WatchlistItem second = watchlistService.addItem(USER_A, watchlist.getId(), new AddWatchlistItemRequest("nimb"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(watchlistService.listItems(USER_A, watchlist.getId())).hasSize(1);
    }

    @Test
    void addingUnknownSymbolIsRejected() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Bad Symbol"));

        assertThatThrownBy(() ->
                watchlistService.addItem(USER_A, watchlist.getId(), new AddWatchlistItemRequest("NOTREAL")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void removingItemFromEmptyOrWrongWatchlistIsNotFound() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Solo"));

        assertThatThrownBy(() -> watchlistService.removeItem(USER_A, watchlist.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reorderingItemPersistsNewPosition() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Reorder"));
        WatchlistItem item = watchlistService.addItem(USER_A, watchlist.getId(), new AddWatchlistItemRequest("SAHY"));

        WatchlistItem moved = watchlistService.updateItemPosition(
                USER_A, watchlist.getId(), item.getId(), new UpdateWatchlistItemRequest(5));

        assertThat(moved.getPosition()).isEqualTo(5);
    }

    @Test
    void userCannotAccessAnotherUsersWatchlist() {
        Watchlist watchlist = watchlistService.createWatchlist(USER_A, new CreateWatchlistRequest("Private"));

        assertThatThrownBy(() -> watchlistService.listItems(USER_B, watchlist.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingNonexistentWatchlistIsNotFound() {
        assertThatThrownBy(() -> watchlistService.deleteWatchlist(USER_A, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
